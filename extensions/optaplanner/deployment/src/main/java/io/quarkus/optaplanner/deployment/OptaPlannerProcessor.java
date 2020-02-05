package io.quarkus.optaplanner.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.ConstraintStreamImplType;
import org.optaplanner.core.config.score.director.ScoreDirectorFactoryConfig;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.SolverManagerConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerListenerBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.optaplanner.OptaPlannerBeanProvider;
import io.quarkus.optaplanner.OptaPlannerObjectMapperCustomizer;
import io.quarkus.optaplanner.OptaPlannerRecorder;

class OptaPlannerProcessor {

    OptaPlannerQuarkusConfig optaPlannerQuarkusConfig;

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FeatureBuildItem.OPTAPLANNER);
    }

    @BuildStep
    void registerAdditionalBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        // The bean encapsulating the SolverFactory
        additionalBeans.produce(new AdditionalBeanBuildItem(OptaPlannerBeanProvider.class));
    }

    @BuildStep(loadsApplicationClasses = true)
    @Record(STATIC_INIT)
    void recordSolverFactory(OptaPlannerRecorder recorder, RecorderContext recorderContext,
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<BeanContainerListenerBuildItem> beanContainerListener) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        SolverConfig solverConfig;
        if (optaPlannerQuarkusConfig.solverConfigXml.isPresent()) {
            String solverConfigXML = optaPlannerQuarkusConfig.solverConfigXml.get();
            if (classLoader.getResource(solverConfigXML) == null) {
                throw new IllegalStateException("Invalid optaplanner.solverConfigXML property (" + solverConfigXML
                        + "): that classpath resource does not exist.");
            }
            solverConfig = SolverConfig.createFromXmlResource(solverConfigXML, classLoader);
        } else if (classLoader.getResource(OptaPlannerQuarkusConfig.DEFAULT_SOLVER_CONFIG_URL) != null) {
            solverConfig = SolverConfig.createFromXmlResource(
                    OptaPlannerQuarkusConfig.DEFAULT_SOLVER_CONFIG_URL, classLoader);
        } else {
            solverConfig = new SolverConfig(classLoader);
        }
        solverConfig.setClassLoader(null); // TODO HACK

        IndexView indexView = combinedIndex.getIndex();

        // Skips extension if no @PlanningEntity or @PlanningSolution classes were found
        if (!applySolverProperties(recorder, recorderContext, indexView, solverConfig)) {
            return;
        }

        SolverManagerConfig solverManagerConfig = new SolverManagerConfig();
        optaPlannerQuarkusConfig.solverManager.parallelSolverCount.ifPresent(solverManagerConfig::setParallelSolverCount);
        beanContainerListener
                .produce(new BeanContainerListenerBuildItem(
                        recorder.initialize(solverConfig, solverManagerConfig)));
    }

    private boolean applySolverProperties(OptaPlannerRecorder recorder, RecorderContext recorderContext,
            IndexView indexView, SolverConfig solverConfig) {
        if (solverConfig.getScanAnnotatedClassesConfig() != null) {
            throw new IllegalArgumentException("Do not use scanAnnotatedClasses with the Quarkus extension,"
                    + " because the Quarkus extension scans too.\n"
                    + "Maybe delete the scanAnnotatedClasses element in the solver config.");
        }

        // Fail fast if solution class exists but not entity class, and vice versa. If both don't exist, skip extension
        Pair<Class<?>, Boolean> solutionClassResult = findSolutionClassResult(recorderContext, indexView);
        List<Class<?>> entityClassList = findEntityClassList(recorderContext, indexView);

        Boolean doesSolutionClassExist = solutionClassResult.getRight();
        Boolean doesEntityClassExist = !entityClassList.isEmpty();

        if (doesSolutionClassExist && !doesEntityClassExist) {
            throw new IllegalStateException("A class with a @" + PlanningSolution.class.getSimpleName()
                    + " annotation was found, but no classes with a @" + PlanningEntity.class.getSimpleName()
                    + " annotation was found.");
        } else if (doesEntityClassExist && !doesSolutionClassExist) {
            throw new IllegalStateException("A class with a @" + PlanningEntity.class.getSimpleName()
                    + " annotation was found, but no classes with a @" + PlanningSolution.class.getSimpleName()
                    + " annotation was found.");
        } else if (!doesSolutionClassExist && !doesEntityClassExist) {
            return false;
        }

        if (solverConfig.getSolutionClass() == null) {
            solverConfig.setSolutionClass(solutionClassResult.getLeft());
        }
        if (solverConfig.getEntityClassList() == null) {
            solverConfig.setEntityClassList(entityClassList);
        }
        if (solverConfig.getScoreDirectorFactoryConfig() == null) {
            ScoreDirectorFactoryConfig scoreDirectorFactoryConfig = new ScoreDirectorFactoryConfig();
            // Use Bavet to avoid Drools classpath issues (drools 7 vs kogito 1 code duplication)
            scoreDirectorFactoryConfig.setConstraintStreamImplType(ConstraintStreamImplType.BAVET);
            scoreDirectorFactoryConfig.setConstraintProviderClass(findConstraintProviderClass(recorderContext, indexView));
            solverConfig.setScoreDirectorFactoryConfig(scoreDirectorFactoryConfig);
        }
        optaPlannerQuarkusConfig.solver.environmentMode.ifPresent(solverConfig::setEnvironmentMode);
        optaPlannerQuarkusConfig.solver.moveThreadCount.ifPresent(solverConfig::setMoveThreadCount);
        applyTerminationProperties(solverConfig);
        return true;
    }

    private Pair<Class<?>, Boolean> findSolutionClassResult(RecorderContext recorderContext, IndexView indexView) {
        Collection<AnnotationInstance> annotationInstances = indexView.getAnnotations(DotNames.PLANNING_SOLUTION);
        if (annotationInstances.size() > 1) {
            throw new IllegalStateException("Multiple classes (" + convertAnnotationInstancesToString(annotationInstances)
                    + ") found with a @" + PlanningSolution.class.getSimpleName() + " annotation.");
        }
        if (annotationInstances.isEmpty()) {
            return new ImmutablePair<>(null, false);
        }

        AnnotationTarget solutionTarget = annotationInstances.iterator().next().target();
        if (solutionTarget.kind() != AnnotationTarget.Kind.CLASS) {
            throw new IllegalStateException("A target (" + solutionTarget
                    + ") with a @" + PlanningSolution.class.getSimpleName() + " must be a class.");
        }

        return new ImmutablePair<>(recorderContext.classProxy(solutionTarget.asClass().name().toString()), true);
    }

    private List<Class<?>> findEntityClassList(RecorderContext recorderContext, IndexView indexView) {
        Collection<AnnotationInstance> annotationInstances = indexView.getAnnotations(DotNames.PLANNING_ENTITY);
        if (annotationInstances.isEmpty()) {
            return Collections.emptyList();
        }
        List<AnnotationTarget> targetList = annotationInstances.stream()
                .map(AnnotationInstance::target)
                .collect(Collectors.toList());
        if (targetList.stream().anyMatch(target -> target.kind() != AnnotationTarget.Kind.CLASS)) {
            throw new IllegalStateException("All targets (" + targetList
                    + ") with a @" + PlanningEntity.class.getSimpleName() + " must be a class.");
        }
        return targetList.stream()
                .map(target -> recorderContext.classProxy(target.asClass().name().toString()))
                .collect(Collectors.toList());
    }

    private Class<? extends ConstraintProvider> findConstraintProviderClass(RecorderContext recorderContext,
            IndexView indexView) {
        Collection<ClassInfo> classInfos = indexView.getAllKnownImplementors(
                DotName.createSimple(ConstraintProvider.class.getName()));
        if (classInfos.size() > 1) {
            throw new IllegalStateException("Multiple classes (" + convertClassInfosToString(classInfos)
                    + ") found that implement the interface " + ConstraintProvider.class.getSimpleName() + ".");
        }
        if (classInfos.isEmpty()) {
            throw new IllegalStateException("No classes (" + convertClassInfosToString(classInfos)
                    + ") found that implement the interface " + ConstraintProvider.class.getSimpleName() + ".");
        }
        // TODO use .asSubclass(ConstraintProvider.class) once https://github.com/quarkusio/quarkus/issues/5630 is fixed
        return (Class<? extends ConstraintProvider>) recorderContext.classProxy(classInfos.iterator().next().name().toString());
    }

    private void applyTerminationProperties(SolverConfig solverConfig) {
        TerminationConfig terminationConfig = solverConfig.getTerminationConfig();
        if (terminationConfig == null) {
            terminationConfig = new TerminationConfig();
            solverConfig.setTerminationConfig(terminationConfig);
        }
        optaPlannerQuarkusConfig.solver.termination.spentLimit.ifPresent(terminationConfig::setSpentLimit);
        optaPlannerQuarkusConfig.solver.termination.unimprovedSpentLimit.ifPresent(terminationConfig::setUnimprovedSpentLimit);
        optaPlannerQuarkusConfig.solver.termination.bestScoreLimit.ifPresent(terminationConfig::setBestScoreLimit);
    }

    private String convertAnnotationInstancesToString(Collection<AnnotationInstance> annotationInstances) {
        return "[" + annotationInstances.stream().map(instance -> instance.target().toString())
                .collect(Collectors.joining(", ")) + "]";
    }

    private String convertClassInfosToString(Collection<ClassInfo> classInfos) {
        return "[" + classInfos.stream().map(instance -> instance.name().toString())
                .collect(Collectors.joining(", ")) + "]";
    }

    // TODO health check

    @BuildStep
    void registerOptaPlannerJacksonModule(BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            Capabilities capabilities) {
        if (!capabilities.isCapabilityPresent(Capabilities.JACKSON)) {
            return;
        }
        try {
            Class.forName("org.optaplanner.persistence.jackson.api.OptaPlannerJacksonModule", false,
                    Thread.currentThread().getContextClassLoader());
        } catch (Exception e) {
            // Fail fast during build to avoid a certain runtime failure
            throw new IllegalStateException(
                    "When using both Jackson and OptaPlanner,"
                            + " add a dependency on org.optaplanner:optaplanner-persistence-jackson too.",
                    e);
        }
        additionalBeans.produce(new AdditionalBeanBuildItem(OptaPlannerObjectMapperCustomizer.class));
    }

    // TODO JAXB customization for Score.class

    // TODO JPA customization for Score.class

}
