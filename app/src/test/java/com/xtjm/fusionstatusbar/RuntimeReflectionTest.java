package com.xtjm.fusionstatusbar;

import org.junit.Test;
import static org.junit.Assert.*;

public class RuntimeReflectionTest {
    @Test public void overloadsAndMissesAreScopedToActualClassAndArguments() throws Exception {
        Host host = new Host();
        assertEquals("number", RuntimeReflection.compatible(Host.class, "set", new Object[] {3}).invoke(host, 3));
        assertEquals("flag", RuntimeReflection.compatible(Host.class, "set", new Object[] {true}).invoke(host, true));
        assertNull(RuntimeReflection.compatible(Host.class, "set", new Object[] {3L}));
        assertNull(RuntimeReflection.compatible(Host.class, "set", new Object[] {3L}));
        assertNotNull(RuntimeReflection.compatible(OtherHost.class, "set", new Object[] {3L}));
        assertNotNull(RuntimeReflection.field(Host.class, "inherited"));
        assertNull(RuntimeReflection.field(OtherHost.class, "inherited"));
        assertSame(RuntimeReflection.field(Host.class, "inherited"), RuntimeReflection.field(Host.class, "inherited"));
    }
    static class Base { int inherited; }
    static class Host extends Base {
        String set(int value) { return "number"; }
        String set(boolean value) { return "flag"; }
    }
    static class OtherHost { void set(long value) { } }
}
