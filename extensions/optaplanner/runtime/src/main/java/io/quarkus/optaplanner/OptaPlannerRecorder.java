package io.quarkus.optaplanner;

import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.config.solver.SolverConfig;

import io.quarkus.arc.runtime.BeanContainerListener;
import io.quarkus.runtime.annotations.Recorder;
import org.optaplanner.core.config.solver.SolverManagerConfig;

@Recorder
public class OptaPlannerRecorder {

    public BeanContainerListener initialize(SolverConfig solverConfig, SolverManagerConfig solverManagerConfig) {
        return container -> {
            OptaPlannerBeanProvider.solverConfig = solverConfig;
            OptaPlannerBeanProvider.solverManagerConfig = solverManagerConfig;
        };
    }

}
