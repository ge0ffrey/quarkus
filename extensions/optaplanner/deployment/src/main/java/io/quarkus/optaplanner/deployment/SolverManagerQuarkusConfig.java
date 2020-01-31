package io.quarkus.optaplanner.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;
import org.optaplanner.core.config.solver.SolverManagerConfig;

/**
 * Subset of OptaPlanner's {@link SolverManagerConfig}.
 */
@ConfigGroup
public class SolverManagerQuarkusConfig {

    /**
     * The number of solvers that run in parallel. This directly influences CPU consumption.
     * Defaults to {@value SolverManagerConfig#PARALLEL_SOLVER_COUNT_AUTO}.
     * Other options include a number or formula based on the available processor count.
     */
    @ConfigItem
    Optional<String> parallelSolverCount;

}
