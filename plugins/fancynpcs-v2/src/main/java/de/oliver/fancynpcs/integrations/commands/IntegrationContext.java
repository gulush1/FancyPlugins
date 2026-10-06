package de.oliver.fancynpcs.integrations.commands;

import de.oliver.fancyanalytics.logger.ExtendedFancyLogger;
import de.oliver.fancylib.translations.Translator;
import de.oliver.fancynpcs.integrations.IntegrationsConfig;
import de.oliver.fancynpcs.integrations.IntegrationsManager;

/**
 * Base class for integration commands, exposing the shared integration services.
 */
public abstract class IntegrationContext {

    protected final IntegrationsManager integrations;
    protected final IntegrationsConfig config;
    protected final ExtendedFancyLogger logger;
    protected final Translator translator;

    protected IntegrationContext() {
        this.integrations = IntegrationsManager.get();
        this.config = integrations.getConfig();
        this.logger = integrations.getLogger();
        this.translator = integrations.getTranslator();
    }

}
