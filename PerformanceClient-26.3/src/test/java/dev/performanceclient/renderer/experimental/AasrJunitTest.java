package dev.performanceclient.renderer.experimental;

import org.junit.jupiter.api.Test;

/** Gradle/JUnit entrypoint for the independent rendering-kernel tests. */
public final class AasrJunitTest {
    @Test void encodedFacesMatchReference() {
        AxisAlgebraSurfaceSelfTest.main(new String[0]);
    }
    @Test void maskedRaycastMatchesReference() {
        AasrSoftwareRendererSelfTest.main(new String[0]);
    }
}
