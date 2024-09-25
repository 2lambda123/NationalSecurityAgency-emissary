package emissary.place;

import emissary.core.IBaseDataObject;
import emissary.core.ResourceException;
import emissary.util.GitRepositoryState;

import java.io.IOException;
import java.io.InputStream;

public class VersionPlace extends ServiceProviderPlace {

    private static final String EMISSARY_VERSION = "EMISSARY_VERSION";
    private static final String EMISSARY_VERSION_HASH = "EMISSARY_VERSION_HASH";
    private boolean includeDate;
    private boolean useAbbrevHash;
    private String formattedVersion;
    private String versionHash;

    /**
     * Create the place from the specified config file or resource
     *
     * @param configInfo the config file or resource to use
     * @param dir the name of the controlling directory to register with
     * @param placeLoc string name of this place
     */
    public VersionPlace(String configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * Create the place from the specified config stream data
     *
     * @param configInfo the config file or resource to use
     * @param dir the name of the controlling directory to register with
     * @param placeLoc string name of this place
     */
    public VersionPlace(InputStream configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * Create the place from the specified config stream data
     *
     * @param configInfo the config file or resource to use
     */
    public VersionPlace(InputStream configInfo) throws IOException {
        super(configInfo);
        configurePlace();
    }

    /**
     * Create with the default configuration
     */
    public VersionPlace() throws IOException {
        super();
        configurePlace();
    }

    private void configurePlace() {
        GitRepositoryState gitRepositoryState = initGitRepositoryState();

        includeDate = configG.findBooleanEntry("INCLUDE_DATE", true);
        useAbbrevHash = configG.findBooleanEntry("USE_ABBREV_HASH", true);
        formattedVersion = getVersion(gitRepositoryState);
        versionHash = getVersionHash(gitRepositoryState);
    }

    @Override
    public void process(IBaseDataObject payload) throws ResourceException {
        payload.putParameter(EMISSARY_VERSION, formattedVersion);
        payload.putParameter(EMISSARY_VERSION_HASH, versionHash);
    }

    GitRepositoryState initGitRepositoryState() {
        return GitRepositoryState.getRepositoryState();
    }

    protected String getVersion(GitRepositoryState gitRepositoryState) {
        if (includeDate) {
            // version with date & time information
            // changes format of date from 2024-09-23T10:41:18-0400, to 20240923104118
            String buildTime = gitRepositoryState.getBuildTime();
            int cutEndMark = buildTime.lastIndexOf(":") + 3;
            String formattedDate = buildTime.substring(0, cutEndMark).replaceAll("\\D", "");
            return gitRepositoryState.getBuildVersion() + "-" + formattedDate;
        } else {
            // adds just version
            return gitRepositoryState.getBuildVersion();
        }
    }

    protected String getVersionHash(GitRepositoryState gitRepositoryState) {
        if (useAbbrevHash) {
            // first 7 chars of commit hash
            return gitRepositoryState.getCommitIdAbbrev();
        } else {
            // full commit hash (default option)
            return gitRepositoryState.getCommitId();
        }
    }

    public void setIncludeDate(Boolean includeDate) {
        this.includeDate = includeDate;
    }

    public void setAbbrevHash(Boolean useAbbrevHash) {
        this.useAbbrevHash = useAbbrevHash;
    }
}
