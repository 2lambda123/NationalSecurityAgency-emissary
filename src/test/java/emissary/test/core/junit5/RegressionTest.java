package emissary.test.core.junit5;

import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectHelper;
import emissary.core.IBaseDataObjectXmlCodecs;
import emissary.core.IBaseDataObjectXmlCodecs.ElementDecoders;
import emissary.core.IBaseDataObjectXmlCodecs.ElementEncoders;

import com.google.errorprone.annotations.ForOverride;
import org.jdom2.Document;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * <p>
 * This test acts similarly to ExtractionTest; however, it compares the entire BDO instead of just what is defined in
 * the XML. In other words, the XML must define exactly the output of the Place, no more and no less. There are methods
 * provided to generate the XML required.
 * </p>
 * 
 * <p>
 * To implement this for a test, you should:
 * </p>
 * <ol>
 * <li>Extend this class</li>
 * <li>Override {@link #generateAnswers()} to return true, which will generate the XML answer files</li>
 * <li>Optionally, override the various provided methods if you want to customise the behaviour of providing the IBDO
 * before/after processing</li>
 * <li>Run the tests, which should pass - if they don't, you either have incorrect processing which needs fixing, or you
 * need to further customise the initial/final IBDOs.</li>
 * <li>Once the tests pass, you can remove the overridden method(s) added above.</li>
 * </ol>
 */
public abstract class RegressionTest extends ExtractionTest {

    /**
     * Override this to generate XML for data files.
     * 
     * @return defaults to false if no XML should be generated (i.e. normal case of executing tests) or true to generate
     *         automatically
     */
    @ForOverride
    protected boolean generateAnswers() {
        return false;
    }

    /**
     * Allow the initial IBDO to be overridden - for example, adding additional previous forms
     * 
     * This is used in the simple case to generate an IBDO from the file on disk and override the filename
     * 
     * @param resource path to the dat file
     * @return the initial IBDO
     */
    @ForOverride
    protected IBaseDataObject getInitialIbdo(final String resource) {
        return RegressionTestUtil.getInitialIbdoWithFormInFilename(resource, kff);
    }

    /**
     * Allow the initial IBDO to be overridden before serialising to XML.
     * 
     * In the default case, we null out the data in the BDO which will force the data to be loaded from the .dat file
     * instead.
     * 
     * @param resource path to the dat file
     * @param initialIbdo to tweak
     */
    @ForOverride
    protected void tweakInitialIbdoBeforeSerialisation(final String resource, final IBaseDataObject initialIbdo) {
        RegressionTestUtil.setDataToNull(initialIbdo);
    }

    /**
     * Allow the generated IBDO to be overridden - for example, adding certain field values. Will modify the provided IBDO.
     * 
     * This is used in the simple case to set the current form for the final object to be taken from the file name. If the
     * test worked correctly no change will be made, but if there is a discrepancy this will be highlighted afterwards when
     * the diff takes place.
     * 
     * @param resource path to the dat file
     * @param finalIbdo the existing final BDO after it's been processed by a place
     */
    @ForOverride
    protected void tweakFinalIbdoBeforeSerialisation(final String resource, final IBaseDataObject finalIbdo) {
        RegressionTestUtil.tweakFinalIbdoWithFormInFilename(resource, finalIbdo);
    }

    /**
     * Allow the children generated by the place to be overridden before serialising to XML.
     * 
     * In the default case, do nothing.
     * 
     * @param resource path to the dat file
     * @param children to tweak
     */
    @ForOverride
    protected void tweakFinalResultsBeforeSerialisation(final String resource, final List<IBaseDataObject> children) {
        // No-op unless overridden
    }

    @Override
    @ForOverride
    protected String getInitialForm(final String resource) {
        return RegressionTestUtil.getInitialFormFromFilename(resource);
    }

    /**
     * This method returns the XML element decoders.
     * 
     * @return the XML element decoders.
     */
    protected ElementDecoders getDecoders() {
        return IBaseDataObjectXmlCodecs.DEFAULT_ELEMENT_DECODERS;
    }

    /**
     * This method returns the XML element encoders.
     * 
     * @return the XML element encoders.
     */
    protected ElementEncoders getEncoders() {
        return IBaseDataObjectXmlCodecs.SHA256_ELEMENT_ENCODERS;
    }

    /**
     * When the data is able to be retrieved from the XML (e.g. when getEncoders() returns the default encoders), then this
     * method should be empty. However, in this case getEncoders() is returning the sha256 encoders which means the original
     * data cannot be retrieved from the XML. Therefore, in order to test equivalence, all of the non-printable data in the
     * IBaseDataObjects needs to be converted to a sha256 hash. The full encoders can be used by overriding the
     * checkAnswersPreHook(...) to be empty and overriding getEncoders() to return the DEFAULT_ELEMENT_ENCODERS.
     */
    @Override
    protected void checkAnswersPreHook(final Document answers, final IBaseDataObject payload, final List<IBaseDataObject> attachments,
            final String tname) {
        if (payload.data() != null && IBaseDataObjectXmlCodecs.hasNonPrintableValues(payload.data())) {
            final String hash = IBaseDataObjectXmlCodecs.sha256Bytes(payload.data());

            if (hash != null) {
                payload.setData(hash.getBytes(StandardCharsets.UTF_8));
            }
        }

        if (payload.getExtractedRecords() != null) {
            for (final IBaseDataObject extractedRecord : payload.getExtractedRecords()) {
                if (IBaseDataObjectXmlCodecs.hasNonPrintableValues(extractedRecord.data())) {
                    final String hash = IBaseDataObjectXmlCodecs.sha256Bytes(extractedRecord.data());

                    if (hash != null) {
                        extractedRecord.setData(hash.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        }

        if (attachments != null) {
            for (final IBaseDataObject attachment : attachments) {
                if (IBaseDataObjectXmlCodecs.hasNonPrintableValues(attachment.data())) {
                    final String hash = IBaseDataObjectXmlCodecs.sha256Bytes(attachment.data());

                    if (hash != null) {
                        attachment.setData(hash.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        }
    }

    // Everything above can be overridden by extending classes to modify behaviour as they see fit.
    // Below this point, methods should not be able to be overridden as they are inherently part of RegressionTest.

    @ParameterizedTest
    @MethodSource("data")
    @Override
    public final void testExtractionPlace(final String resource) {
        logger.debug("Running {} test on resource {}", place.getClass().getName(), resource);

        if (generateAnswers()) {
            try {
                generateAnswerFiles(resource);
            } catch (final Exception e) {
                logger.error("Error running test {}", resource, e);
                fail("Unable to generate answer file", e);
            }
        }

        // Run the normal extraction/regression tests
        super.testExtractionPlace(resource);
    }

    /**
     * Actually generate the answer file for a given resource
     * 
     * Takes initial form & final forms from the filename
     * 
     * @param resource to generate against
     * @throws Exception if an error occurs during processing
     */
    private void generateAnswerFiles(final String resource) throws Exception {
        // Get the data and create a channel factory to it
        final IBaseDataObject initialIbdo = getInitialIbdo(resource);
        // Clone the BDO to create an 'after' copy
        final IBaseDataObject finalIbdo = IBaseDataObjectHelper.clone(initialIbdo, true);
        // Actually process the BDO and keep the children
        final List<IBaseDataObject> finalResults = place.agentProcessHeavyDuty(finalIbdo);

        // Allow overriding things before serialising to XML
        tweakInitialIbdoBeforeSerialisation(resource, initialIbdo);
        tweakFinalIbdoBeforeSerialisation(resource, finalIbdo);
        tweakFinalResultsBeforeSerialisation(resource, finalResults);

        // Generate the full XML (setup & answers from before & after)
        RegressionTestUtil.writeAnswerXml(resource, initialIbdo, finalIbdo, finalResults, getEncoders());
    }

    @Override
    protected final Document getAnswerDocumentFor(final String resource) {
        // If generating answers, get the src version, otherwise get the normal XML file
        return generateAnswers() ? RegressionTestUtil.getAnswerDocumentFor(resource) : super.getAnswerDocumentFor(resource);
    }

    @Override
    protected final void setupPayload(final IBaseDataObject payload, final Document answers) {
        RegressionTestUtil.setupPayload(payload, answers, getDecoders());
    }

    @Override
    protected final void checkAnswers(final Document answers, final IBaseDataObject payload,
            final List<IBaseDataObject> attachments, final String tname) {
        RegressionTestUtil.checkAnswers(answers, payload, attachments, place.getClass().getName(), getDecoders());
    }
}
