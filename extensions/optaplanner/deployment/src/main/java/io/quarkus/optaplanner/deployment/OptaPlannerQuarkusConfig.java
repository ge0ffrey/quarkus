package io.quarkus.optaplanner.deployment;

import java.util.Optional;

import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.SolverManagerConfig;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigRoot;

// Not named OptaPlannerConfig because classes ending with just "Config" collide with OptaPlanner's API
@ConfigRoot(name = "optaplanner")
public class OptaPlannerQuarkusConfig {

    public static final String DEFAULT_SOLVER_CONFIG_URL = "solverConfig.xml";

    /**
     * A classpath resource to read the solver configuration XML.
     * Defaults to {@value DEFAULT_SOLVER_CONFIG_URL}.
     * If this property isn't specified, that solverConfig.xml is optional.
     */
    @ConfigItem
    Optional<String> solverConfigXml;

    /**
     * Configuration properties that overwrite OptaPlanner's {@link SolverConfig}.
     */
    @ConfigItem
    SolverQuarkusConfig solver;
    /**
     * Configuration properties that overwrite OptaPlanner's {@link SolverManagerConfig}.
     */
    @ConfigItem
    SolverManagerQuarkusConfig solverManager;

}
