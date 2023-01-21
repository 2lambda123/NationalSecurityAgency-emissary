package emissary.place;

import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.kff.KffDataObjectHandler;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the KffHashPlace
 */
class KffHashPlaceTest extends UnitTest {

    final String phonyData = "Some phony data to hash";

    public KffHashPlaceTest() {}

    @Test
    void testProcess() throws Exception {
        final KffHashPlace place = new KffHashPlace("KffHashPlace");

        final IBaseDataObject toHash = new BaseDataObject(this.phonyData.getBytes(UTF_8), "phony_data");
        place.process(toHash);
        assertTrue(KffDataObjectHandler.hashPresent(toHash));

        final IBaseDataObject orNotToHash = new BaseDataObject(this.phonyData.getBytes(UTF_8), "phony_data");
        orNotToHash.setParameter(KffHashPlace.SKIP_KFF_HASH, "TRUE");
        assertFalse(KffDataObjectHandler.hashPresent(orNotToHash));
    }
}
