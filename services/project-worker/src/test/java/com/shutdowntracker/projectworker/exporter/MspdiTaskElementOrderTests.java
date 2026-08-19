package com.shutdowntracker.projectworker.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * The element order is read out of MPXJ's MSPDI binding rather than transcribed here, which means
 * an MPXJ upgrade can change it. These tests are what makes such a change visible: they assert the
 * relative order the candidate writer depends on, so a binding that stopped reporting it fails here
 * rather than in a candidate a planner cannot open.
 */
class MspdiTaskElementOrderTests {

    @Test
    void readsTheMspdiTaskSequenceFromTheBinding() {
        assertThat(MspdiTaskElementOrder.positionOf("UID")).isZero();
        assertThat(MspdiTaskElementOrder.positionOf("ID"))
                .isLessThan(MspdiTaskElementOrder.positionOf("Name"));
    }

    /**
     * The three approved execution inputs sit after the structural elements a source always
     * carries and before the dependency links, which is what makes inserting one a placement
     * decision rather than an append.
     */
    @Test
    void ordersTheApprovedExecutionInputsAfterTaskStructureAndBeforeDependencies() {
        assertThat(MspdiTaskElementOrder.positionOf("Summary"))
                .isLessThan(MspdiTaskElementOrder.positionOf("PercentComplete"));
        assertThat(MspdiTaskElementOrder.positionOf("PercentComplete"))
                .isLessThan(MspdiTaskElementOrder.positionOf("ActualStart"));
        assertThat(MspdiTaskElementOrder.positionOf("ActualStart"))
                .isLessThan(MspdiTaskElementOrder.positionOf("ActualFinish"));
        assertThat(MspdiTaskElementOrder.positionOf("ActualFinish"))
                .isLessThan(MspdiTaskElementOrder.positionOf("PredecessorLink"));
    }

    @Test
    void refusesToPlaceAnElementTheMspdiTaskSequenceDoesNotDeclare() {
        assertThatThrownBy(() -> MspdiTaskElementOrder.positionOf("UnmodelledTaskElement"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'UnmodelledTaskElement' is not an MSPDI task element.");
    }

    /**
     * An element the binding does not model reports no position at all, rather than a position that
     * would let it decide where an approved field belongs.
     */
    @Test
    void reportsNoPositionForAnElementTheBindingDoesNotModel() {
        assertThat(MspdiTaskElementOrder.knownPositionOf("UnmodelledTaskElement"))
                .isEqualTo(OptionalInt.empty());
        assertThat(MspdiTaskElementOrder.knownPositionOf("Summary")).isPresent();
    }
}
