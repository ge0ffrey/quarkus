package io.quarkus.optaplanner.constraints;

import org.optaplanner.core.api.score.buildin.simple.SimpleScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;

import io.quarkus.optaplanner.domain.TestdataPlanningEntity;

public class TestdataPlanningConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                factory.from(TestdataPlanningEntity.class)
                        .join(TestdataPlanningEntity.class, Joiners.equal(TestdataPlanningEntity::getValue))
                        .filter((a, b) -> a != b)
                        .penalize("Don't assign 2 entities the same value.", SimpleScore.ONE)
        };
    }

}
