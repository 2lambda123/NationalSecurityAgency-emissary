package emissary.output.roller.journal;

import static emissary.output.roller.journal.Journal.CURRENT_VERSION;
import static emissary.output.roller.journal.Journal.ENTRY_LENGTH;
import static emissary.output.roller.journal.Journal.ERROR_EXT;
import static emissary.output.roller.journal.Journal.EXT;
import static emissary.output.roller.journal.Journal.MAGIC;
import static emissary.output.roller.journal.Journal.NINE;
import static emissary.output.roller.journal.Journal.SEP;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates logic to read/deserialize a BG Journal file.
 */
public class JournalReader implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(JournalReader.class);

    // full path to journal file
    final Path journalPath;
    // channel position where entries begin
    private long begin;
    // initial sequence value
    private long startsequence;
    // current sequence value
    private long sequence;
    // persisted journal
    FileChannel journalChannel;
    // not final to release on close
    private ByteBuffer b = ByteBuffer.allocateDirect(ENTRY_LENGTH);

    private final Journal journal;

    private final ReentrantLock lock = new ReentrantLock();

    public JournalReader(final Path journalPath) throws IOException {
        this.journalPath = journalPath;
        journal = new Journal(journalPath);
        checkJournal();
        loadEntries();
    }

    private void checkJournal() throws IOException {
        if (Files.exists(journalPath) && Files.size(journalPath) > 0L) {
            readHeader();
        } else {
            throw new NoSuchFileException("File does not exist " + journalPath);
        }
    }

    private void loadEntries() throws IOException {
        journalChannel.position(begin);
        sequence = startsequence;

        while (journalChannel.position() < journalChannel.size()) {
            int read = read(ENTRY_LENGTH);
            if (read != ENTRY_LENGTH) {
                logger.warn("Incomplete journal read for {}. Expected {} but read {}. Sequence start {}, last successful sequence {} ({}th)",
                        journal.getKey(), ENTRY_LENGTH, read, startsequence, sequence, (sequence - startsequence));
                break;
            }
            long nextSeq = getSequence();
            if (nextSeq != (++sequence)) {
                logger.warn("Incorrect sequence value returned. Expected {} Received {}. Exiting", sequence, nextSeq);
                break;
            }
            JournalEntry e = JournalEntry.deserialize(b);
            journal.entries.add(e);
        }
    }

    public Journal getJournal() throws IOException {
        return journal;
    }

    // read long followed by null sep. return -1 if any issues
    private long getSequence() throws IOException {
        long seq = b.getLong();
        if (b.get() != SEP) {
            return -1;
        }
        return seq;
    }

    private void checkMagic() throws IOException {
        read(MAGIC.length);
        byte[] mgic = new byte[MAGIC.length];
        b.get(mgic);
        if (!Arrays.equals(mgic, MAGIC)) {
            throw new IllegalStateException("Not a Journal file. Invalid Magic");
        }
    }

    private byte readVersion() throws IOException {
        read(1);
        return b.get();
    }

    private void readHeader() throws IOException {
        this.journalChannel = FileChannel.open(journalPath, StandardOpenOption.CREATE, StandardOpenOption.READ);
        checkMagic();
        journal.setVersion(readVersion());

        if (journal.version != CURRENT_VERSION) {
            // should not happen now, but future versions may delegate to a legacy reader
            throw new IllegalStateException("Incorrect Version Detected " + journal.version);
        }
        journal.setKey(readString());
        // starting sequence number.
        read(NINE);
        sequence = getSequence();
        if (sequence == -1) {
            throw new IllegalStateException("Invalid sequence read " + sequence);
        }
        startsequence = sequence;
        begin = journalChannel.position();
    }

    private String readString() throws IOException {
        int read = read(4);
        int len = b.getInt();
        if (read < 0 || len < 1) {
            throw new IllegalStateException("Negative value returned. Possible corrupt file");
        }
        byte[] keyBytes = new byte[len];
        read(len);
        b.get(keyBytes);
        return new String(keyBytes);
    }

    // attempts to read limit bytes from buffer
    private int read(int limit) throws IOException {
        if (limit == 0) {
            return 0;
        }
        b.clear();
        b.limit(limit);
        int total = 0;
        while (total < limit) {
            int read = journalChannel.read(b);
            if (read == -1) {
                logger.debug("EOF when trying to read {} bytes", limit);
                if (total == 0) {
                    total = -1;
                }
                break;
            }
            total += read;
        }
        b.flip();
        return total;
    }

    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            if (journalChannel != null) {
                journalChannel.close();
            }
            journalChannel = null;
            b = null;
        } finally {
            lock.unlock();
        }
    }

    public static Collection<Path> getJournalPaths(Path dir) throws IOException {
        ArrayList<Path> paths = new ArrayList<>();
        logger.trace("Looking for journal files in {}", dir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + EXT)) {
            for (Path path : stream) {
                logger.trace("Found journal file {}", path);
                paths.add(path);
            }
        }
        return paths;
    }

    /**
     * Search for {@link Journal} files in a directory
     *
     * @param dir the directory to search
     * @return collection of Journal files
     * @throws IOException if an error occurs
     */
    public static Collection<Journal> getJournals(Path dir) throws IOException {
        return convert(getJournalPaths(dir));
    }

    /**
     * Walk a file tree and search for {@link Journal} files
     *
     * @param dir the starting directory to search
     * @param depth the maximum number of directory levels to search
     * @return collection of {@link Journal} files
     * @throws IOException if an error occurs
     */
    public static Collection<Journal> getJournals(Path dir, int depth) throws IOException {
        logger.trace("Looking for journal files in {}, depth {}", dir, depth);
        try (Stream<Path> walk = Files.find(dir, depth, ((path, attrs) -> path.toString().endsWith(EXT)))) {
            return convert(walk);
        }
    }

    /**
     * Get {@link Journal} files from a list of {@link Path}s
     *
     * @param journals the {@link Journal} paths
     * @return collection of {@link Journal} files
     * @throws IOException if an error occurs
     */
    public static Collection<Journal> getJournals(Collection<Path> journals) throws IOException {
        return convert(journals);
    }

    /**
     * Given a collection of {@link Path}s, create a set of {@link Journal}s grouped using the {@link Journal#getKey()}
     *
     * @param journals the {@link Journal} paths
     * @return collection of {@link Journal} files
     * @throws IOException if an error occurs
     */
    public static Map<String, List<Journal>> getJournalsGroupedByKey(Collection<Path> journals) throws IOException {
        return convert(journals).stream()
                .collect(Collectors.groupingBy(Journal::getKey));
    }

    /**
     * Convert a collection of paths into a collection of journals
     *
     * @param journals collection of {@link Journal} paths
     * @return collection of {@link Journal}s
     */
    protected static Collection<Journal> convert(Collection<Path> journals) {
        return convert(journals.stream());
    }

    /**
     * Convert a collection of paths into a collection of journals
     *
     * @param stream of {@link Journal} paths
     * @return collection of {@link Journal}s
     */
    protected static Collection<Journal> convert(Stream<Path> stream) {
        return stream
                .map(JournalReader::getJournal)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Read a {@link Journal} from a specified {@link Path} using a {@link JournalReader}.
     *
     * @param path the path of the {@link Journal} file
     * @return the loaded Journal
     */
    public static Journal getJournal(Path path) {
        logger.trace("Reading journal file {}", path);
        if (Files.exists(path) && Files.isReadable(path) && !Files.isDirectory(path)) {
            try (JournalReader jr = new JournalReader(path)) {
                logger.trace("Loaded journal file {}", path);
                return jr.getJournal();
            } catch (IOException ex) {
                logger.error("Unable to load Journal {}", path.toString(), ex);
                renameToError(path);
            }
        } else {
            logger.warn("Unable to access the Journal file {}", path);
        }
        return null;
    }

    public static void renameToError(Path path) {
        try {
            Path errorPath = Paths.get(path.toString() + ERROR_EXT);
            Files.move(path, errorPath);
        } catch (IOException ex) {
            logger.warn("Unable to rename file {}.", path.toString(), ex);
        }
    }

    /**
     * Prints contents of a Journal to stdout.
     */
    public static void main(String[] args) throws Exception {
        String path = args[0];
        try (JournalReader jr = new JournalReader(Paths.get(path))) {
            Journal j = jr.getJournal();
            System.out.println("Journal File: " + path);
            System.out.println("Journal Version: " + j.getKey());
            for (JournalEntry je : j.getEntries()) {
                System.out.println(je.toString());
            }
        }
    }
}
