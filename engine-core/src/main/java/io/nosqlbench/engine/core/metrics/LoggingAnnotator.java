package io.nosqlbench.engine.core.metrics;

import io.nosqlbench.nb.annotations.Service;
import io.nosqlbench.nb.api.annotations.Annotation;
import io.nosqlbench.nb.api.annotations.Annotator;
import io.nosqlbench.nb.api.config.ConfigAware;
import io.nosqlbench.nb.api.config.ConfigModel;
import io.nosqlbench.nb.api.config.ConfigReader;
import io.nosqlbench.nb.api.config.MutableConfigModel;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

@Service(value = Annotator.class, selector = "log")
public class LoggingAnnotator implements Annotator, ConfigAware {

    private final static Logger logger = LogManager.getLogger("ANNOTATORS");
    private final static Logger annotationsLog = LogManager.getLogger("ANNOTATIONS");
    private Level level;

    private final Map<String, String> tags = new LinkedHashMap<>();

    public LoggingAnnotator() {
    }

    @Override
    public void recordAnnotation(Annotation annotation) {
        String inlineForm = annotation.asJson();
        annotationsLog.log(level, inlineForm);
    }

    /**
     * @return The annotated selector of this implementation,
     * ensuring that selector and name stay the same
     */
    @Override
    public String getName() {
        String selector = LoggingAnnotator.class.getAnnotation(Service.class).selector();
        return selector;
    }

    @Override
    public void applyConfig(Map<String, ?> providedConfig) {
        ConfigModel configModel = getConfigModel();
        ConfigReader cfg = configModel.apply(providedConfig);
        String levelName = cfg.param("level", String.class);
        this.level = Level.valueOf(levelName);
    }

    @Override
    public ConfigModel getConfigModel() {
        return new MutableConfigModel(this)
                .defaultto("level", "INFO",
                        "The logging level to use for this annotator")
                .asReadOnly();
    }

}
