package emissary.spi;

import emissary.core.MetadataDictionary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class MetadataDictionaryInitializationProvider implements InitializationProvider {
    protected static Logger logger = LoggerFactory.getLogger(MetadataDictionaryInitializationProvider.class);

    @Override
    public void initialize() {
        // / Initialize the metadata dictionary
        MetadataDictionary.initialize();
    }

    @Override
    public void shutdown() {
        try {
            MetadataDictionary.lookup().shutdown();
        } catch (Exception e) {
            logger.warn("no metadata dictionary available");
        }
    }
}