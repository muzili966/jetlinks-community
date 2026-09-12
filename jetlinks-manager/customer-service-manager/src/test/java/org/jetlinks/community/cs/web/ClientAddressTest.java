package org.jetlinks.community.cs.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientAddressTest {

    @Test
    void forwardedForTakesFirstHop() {
        assertEquals("1.2.3.4", ClientAddress.resolve("1.2.3.4, 10.0.0.1, 10.0.0.2", "10.0.0.2"));
        assertEquals("1.2.3.4", ClientAddress.resolve(" 1.2.3.4 ", "10.0.0.2"));
    }

    @Test
    void fallsBackToRemoteHost() {
        assertEquals("10.0.0.2", ClientAddress.resolve(null, "10.0.0.2"));
        assertEquals("10.0.0.2", ClientAddress.resolve("", "10.0.0.2"));
        assertEquals("10.0.0.2", ClientAddress.resolve(" , 1.1.1.1", "10.0.0.2"));
    }

    @Test
    void unknownWhenNothingAvailable() {
        assertEquals(ClientAddress.UNKNOWN, ClientAddress.resolve(null, null));
        assertEquals(ClientAddress.UNKNOWN, ClientAddress.resolve("  ", " "));
    }
}
