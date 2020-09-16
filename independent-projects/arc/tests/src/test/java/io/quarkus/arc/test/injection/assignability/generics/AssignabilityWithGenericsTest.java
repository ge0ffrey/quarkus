package io.quarkus.arc.test.injection.assignability.generics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.arc.Arc;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.test.ArcTestContainer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.inject.Produces;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class AssignabilityWithGenericsTest {

    @RegisterExtension
    public ArcTestContainer container = new ArcTestContainer(Car.class, Engine.class, PetrolEngine.class,
            Vehicle.class,
            StringListConsumer.class, ListConsumer.class, ProducerBean.class, DefinitelyNotBar.class,
            Bar.class, GenericInterface.class, AlmostCompleteBean.class, ActualBean.class,
            BetaFace.class, GammaFace.class, GammaImpl.class, AbstractAlpha.class, AlphaImpl.class,
            BeanInjectingActualType.class, FooTyped.class,
            ScoreManager1.class, MyScore1.class, ScoreManagerBeanProvider1.class,
            ScoreManager2.class, Score2.class, MyScore2.class, ScoreManagerBeanProvider2.class
//            , ScoreManager3.class, Score3.class, MyScore3.class, ScoreManagerBeanProvider3.class
    );

    @Test
    public void testSelectingInstanceOfCar() {
        InstanceHandle<Car> instance = Arc.container().instance(Car.class);
        assertTrue(instance.isAvailable());
        assertNotNull(instance.get().getEngine());
    }

    @Test
    public void testParameterizedTypeWithTypeVariable() {
        InstanceHandle<StringListConsumer> instance = Arc.container().instance(StringListConsumer.class);
        assertTrue(instance.isAvailable());
        assertNotNull(instance.get().getList());
    }

    @Test
    public void testHierarchyWithInterfacesAndMap() {
        InstanceHandle<ActualBean> instance = Arc.container().instance(ActualBean.class);
        assertTrue(instance.isAvailable());
        assertNotNull(instance.get().getInjectedMap());
    }

    @Test
    public void testProxiedBeanWithGenericMethodParams() {
        InstanceHandle<AlphaImpl> alphaInstance = Arc.container().instance(AlphaImpl.class);
        InstanceHandle<GammaImpl> gammaInstance = Arc.container().instance(GammaImpl.class);
        assertTrue(alphaInstance.isAvailable());
        assertTrue(gammaInstance.isAvailable());
        AlphaImpl alpha = alphaInstance.get();
        assertEquals(GammaImpl.class.getSimpleName(), alpha.ping(alpha.getParam()));
    }

    @SuppressWarnings("serial")
    @Test
    public void testRequiredTypeIsActualTypeAndBeanHasObject() {
        InstanceHandle<FooTyped<Object>> fooTypedInstance = Arc.container().instance(new TypeLiteral<FooTyped<Object>>() {
        });
        assertTrue(fooTypedInstance.isAvailable());
        InstanceHandle<BeanInjectingActualType> beanInjectingActualTypeInstance = Arc.container()
                .instance(BeanInjectingActualType.class);
        assertTrue(beanInjectingActualTypeInstance.isAvailable());
    }

    @ApplicationScoped
    static class BeanInjectingActualType {
        @Inject
        FooTyped<Long> bean;
    }

    @Dependent
    static class FooTyped<T> {
    }

    interface GenericInterface<T, K> {

    }

    interface BetaFace<K> {
        K ping();
    }

    interface GammaFace extends BetaFace<String> {

    }

    @Dependent
    static class StringListConsumer extends ListConsumer<String> {

    }

    static class ListConsumer<T> {

        @Inject
        List<T> list;

        public List<T> getList() {
            return list;
        }
    }

    @Dependent
    static class ProducerBean {

        @Produces
        String foo = "foo";

        @Produces
        List<String> produceList() {
            return new ArrayList<>();
        }

        @Produces
        Map<String, Bar> produceMap() {
            return new HashMap<>();
        }

    }

    static class DefinitelyNotBar<D> {

    }

    @ApplicationScoped
    static class Bar extends DefinitelyNotBar<Integer> {

    }

    static abstract class AlmostCompleteBean<T, K extends DefinitelyNotBar<Integer>> implements GenericInterface<T, K> {

        @Inject
        Map<T, K> injectedMap;

        public void observeSomething(@Observes String event, T injectedInstance) {
            // inject-ability is verified at bootstrap
        }

        public void observeSomethingElse(@ObservesAsync String event, K injectedInstance) {
            // inject-ability is verified at bootstrap
        }

        public Map<T, K> getInjectedMap() {
            return injectedMap;
        }
    }

    @ApplicationScoped
    static class ActualBean extends AlmostCompleteBean<String, Bar> {

    }

    @ApplicationScoped
    static class GammaImpl implements GammaFace {

        @Override
        public String ping() {
            return GammaImpl.class.getSimpleName();
        }
    }

    static abstract class AbstractAlpha<T extends BetaFace> {

        @Inject
        T param;

        public T getParam() {
            return param;
        }

        public String ping(T unusedParam) {
            return this.param.ping().toString();
        }

    }

    @ApplicationScoped
    static class AlphaImpl extends AbstractAlpha<GammaFace> {

    }




    // This succeeds

    @Test
    public void testOptaPlanner_object() {
        InstanceHandle<ScoreManager1<MyScore1>> instance = Arc.container().instance(
                new TypeLiteral<ScoreManager1<MyScore1>>() {});
        assertTrue(instance.isAvailable());
    }

    static class ScoreManager1<S> {}

    static class MyScore1 {}

    static class ScoreManagerBeanProvider1 {

        @DefaultBean
        @Singleton
        @Produces
        // SUCCESS: This matches with ScoreManager1<MySolution>
        <S> ScoreManager1<S> scoreManager() {
            return new ScoreManager1<>();
        }
    }

    // This succeeds

    @Test
    public void testOptaPlanner_score_untyped() {
        InstanceHandle<ScoreManager2<MyScore2>> instance = Arc.container().instance(
                new TypeLiteral<ScoreManager2<MyScore2>>() {});
        assertTrue(instance.isAvailable());
    }

    static class ScoreManager2<S extends Score2> {}

    interface Score2 {}

    static class MyScore2 implements Score2 {}

    static class ScoreManagerBeanProvider2 {

        @DefaultBean
        @Singleton
        @Produces
        // SUCCESS: This matches with ScoreManager2<MyScore2>
        <S extends Score2> ScoreManager2<S> scoreManager() {
            return new ScoreManager2<>();
        }
    }

    // This fails

//    @Test
//    public void testOptaPlanner_score_typed_too() {
//        InstanceHandle<ScoreManager3<MyScore3>> instance = Arc.container().instance(
//                new TypeLiteral<ScoreManager3<MyScore3>>() {});
//        assertTrue(instance.isAvailable());
//    }
//
//    static class ScoreManager3<S extends Score3<S>> {}
//
//    interface Score3<S extends Score3<S>> {}
//
//    static class MyScore3 implements Score3<MyScore3> {}
//
//    static class ScoreManagerBeanProvider3 {
//
//        @DefaultBean
//        @Singleton
//        @Produces
//        // FAILURE (BUG): This should match with ScoreManager3<MyScore3>
//        <S extends Score3<S>> ScoreManager3<S> scoreManager() {
//            return new ScoreManager3<>();
//        }
//    }

}
