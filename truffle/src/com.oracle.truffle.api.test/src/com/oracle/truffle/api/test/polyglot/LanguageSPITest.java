/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot;

import static com.oracle.truffle.api.test.polyglot.ValueAssert.assertFails;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.assertValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.LanguageSPITestLanguage.LanguageContext;

public class LanguageSPITest {

    static LanguageContext langContext;

    @Test
    public void testContextClose() {
        langContext = null;
        Engine engine = Engine.create();

        Context context = Context.create(LanguageSPITestLanguage.ID);
        assertTrue(context.initialize(LanguageSPITestLanguage.ID));
        assertNotNull(langContext);
        assertEquals(0, langContext.disposeCalled);
        context.close();
        engine.close();
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testPolyglotClose() {
        langContext = null;
        Engine engine = Engine.create();

        Context context = Context.newBuilder().engine(engine).build();
        context.initialize(LanguageSPITestLanguage.ID);

        assertNotNull(langContext);

        assertEquals(0, langContext.disposeCalled);
        context.close();
        engine.close();
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testImplicitClose() {
        Engine engine = Engine.create();
        langContext = null;
        Context c = Context.newBuilder().engine(engine).build();
        c.initialize(LanguageSPITestLanguage.ID);
        LanguageContext context1 = langContext;

        Context.newBuilder().engine(engine).build().initialize(LanguageSPITestLanguage.ID);
        LanguageContext context2 = langContext;

        c.close();
        engine.close();
        assertEquals(1, context1.disposeCalled);
        assertEquals(1, context2.disposeCalled);
    }

    @Test
    public void testImplicitCloseFromOtherThread() throws InterruptedException {
        Engine engine = Engine.create();
        Context context = Context.newBuilder().engine(engine).build();
        langContext = null;
        context.initialize(LanguageSPITestLanguage.ID);

        Thread t = new Thread(new Runnable() {
            public void run() {
                engine.close();
            }
        });
        t.start();
        t.join(10000);
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testCreateFromOtherThreadAndCloseFromMain() throws InterruptedException {
        Engine engine = Engine.create();
        langContext = null;
        Thread t = new Thread(new Runnable() {
            public void run() {
                Context context = Context.newBuilder().engine(engine).build();
                context.initialize(LanguageSPITestLanguage.ID);
            }
        });
        t.start();
        t.join(10000);
        engine.close();
        assertEquals(1, langContext.disposeCalled);
    }

    private static Value eval(Context context, Function<Env, Object> f) {
        LanguageSPITestLanguage.runinside = f;
        try {
            return context.eval(LanguageSPITestLanguage.ID, "");
        } finally {
            LanguageSPITestLanguage.runinside = null;
        }
    }

    @Test
    public void testContextCloseInsideFromSameThread() {
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID).build();
        eval(context, new Function<Env, Object>() {
            public Object apply(Env t) {
                context.close();
                return null;
            }
        });
        context.close();
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testContextCloseInsideFromSameThreadCancelExecution() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID).engine(engine).build();
        eval(context, new Function<Env, Object>() {
            public Object apply(Env t) {
                context.close(true);
                return null;
            }
        });
        engine.close();
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testEngineCloseInsideFromSameThread() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID).engine(engine).build();
        eval(context, new Function<Env, Object>() {
            public Object apply(Env t) {
                engine.close();
                return null;
            }
        });
        assertEquals(1, langContext.disposeCalled);
        engine.close();
    }

    @Test
    public void testEngineCloseInsideFromSameThreadCancelExecution() {
        Engine engine = Engine.create();
        langContext = null;
        Context context = Context.newBuilder(LanguageSPITestLanguage.ID).engine(engine).build();
        eval(context, new Function<Env, Object>() {
            public Object apply(Env t) {
                engine.close(true);
                return null;
            }
        });
        assertEquals(1, langContext.disposeCalled);
        engine.close();
    }

    @SuppressWarnings("serial")
    private static class Interrupted extends RuntimeException implements TruffleException {

        public boolean isCancelled() {
            return true;
        }

        public Node getLocation() {
            return null;
        }
    }

    @SuppressWarnings("serial")
    private static final class ParseException extends RuntimeException implements TruffleException {
        private final Source source;
        private final int start;
        private final int length;

        ParseException(final Source source, final int start, final int length) {
            Objects.requireNonNull(source, "Source must be non null");
            this.source = source;
            this.start = start;
            this.length = length;
        }

        @Override
        public boolean isSyntaxError() {
            return true;
        }

        @Override
        public Node getLocation() {
            return null;
        }

        @Override
        public SourceSection getSourceLocation() {
            return source.createSection(start, length);
        }
    }

    @Test
    public void testCancelExecutionWhileSleeping() throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(1);
        try {
            Engine engine = Engine.create();
            Context context = Context.newBuilder(LanguageSPITestLanguage.ID).engine(engine).build();

            CountDownLatch beforeSleep = new CountDownLatch(1);
            CountDownLatch interrupt = new CountDownLatch(1);
            AtomicInteger gotInterrupt = new AtomicInteger(0);
            Function<Env, Object> f = new Function<Env, Object>() {
                public Object apply(Env t) {
                    try {
                        beforeSleep.countDown();
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        gotInterrupt.incrementAndGet();
                        interrupt.countDown();
                        throw new Interrupted();
                    }
                    return null;
                }
            };
            Future<Value> future = service.submit(() -> eval(context, f));
            beforeSleep.await(10000, TimeUnit.MILLISECONDS);
            context.close(true);

            interrupt.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(1, gotInterrupt.get());

            try {
                future.get();
                fail();
            } catch (ExecutionException e) {
                PolyglotException polyglotException = (PolyglotException) e.getCause();
                assertTrue(polyglotException.isCancelled());
            }
            engine.close();
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void testImplicitEngineClose() {
        Context context = Context.create();
        context.close();

        try {
            context.getEngine().getOptions();
            fail();
        } catch (IllegalStateException e) {
            // expect closed
        }

        try {
            context.getEngine().getLanguages();
            fail();
        } catch (IllegalStateException e) {
            // expect closed
        }

        try {
            context.getEngine().getInstruments();
            fail();
        } catch (IllegalStateException e) {
            // expect closed
        }

        // does not fail
        context.getEngine().close();
    }

    @Test
    public void testLookupHost() {
        Context context = Context.newBuilder().allowHostAccess(true).build();
        Value value = eval(context, new Function<Env, Object>() {
            public Object apply(Env t) {
                return t.lookupHostSymbol("java.util.HashMap");
            }
        });
        assertTrue(value.isHostObject());
        Object map = value.asHostObject();
        assertSame(map, HashMap.class);
        context.close();
    }

    @Test
    public void testLookupHostArray() {
        Context context = Context.newBuilder().allowHostAccess(true).build();
        Value value = eval(context, new Function<Env, Object>() {
            public Object apply(Env t) {
                return t.lookupHostSymbol("java.lang.String[]");
            }
        });
        assertTrue(value.isHostObject());
        Object map = value.asHostObject();
        assertSame(map, String[].class);
        context.close();
    }

    @Test
    public void testLookupHostDisabled() {
        Context context = Context.newBuilder().allowHostAccess(false).build();
        try {
            eval(context, new Function<Env, Object>() {
                public Object apply(Env t) {
                    return t.lookupHostSymbol("java.util.HashMap");
                }
            });
            fail();
        } catch (PolyglotException e) {
            assertTrue(!e.isInternalError());
        }
        context.close();
    }

    @Test
    public void testIsHostAccessAllowed() {
        Context context = Context.newBuilder().allowHostAccess(false).build();
        assertTrue(!eval(context, env -> env.isHostLookupAllowed()).asBoolean());
        context.close();

        context = Context.newBuilder().allowHostAccess(true).build();
        assertTrue(eval(context, env -> env.isHostLookupAllowed()).asBoolean());
        context.close();
    }

    @Test
    public void testInnerContext() {
        Context context = Context.create();
        Function<Env, Object> f = new Function<Env, Object>() {
            public Object apply(Env env) {
                LanguageContext outerLangContext = LanguageSPITestLanguage.getContext();
                Object config = new Object();
                TruffleContext innerContext = env.newContextBuilder().config("config", config).build();
                Object p = innerContext.enter();
                LanguageContext innerLangContext = LanguageSPITestLanguage.getContext();
                try {

                    try {
                        innerContext.close();
                        fail("context could be closed when entered");
                    } catch (IllegalStateException e) {
                    }
                    assertEquals(0, innerLangContext.disposeCalled);

                    assertEquals(config, innerLangContext.config.get("config"));
                    assertNotSame(outerLangContext, innerLangContext);

                    boolean assertions = false;
                    assert (assertions = true) == true;
                    if (assertions) {
                        boolean leaveFailed = false;
                        try {
                            innerContext.leave("foo");
                        } catch (AssertionError e) {
                            leaveFailed = true;
                        }
                        if (!leaveFailed) {
                            fail("no assertion error for leaving with the wrong object");
                        }
                    }
                } finally {
                    innerContext.leave(p);
                }
                assertSame(outerLangContext, LanguageSPITestLanguage.getContext());
                innerContext.close();

                try {
                    innerContext.enter();
                    fail("cannot be entered after closing");
                } catch (IllegalStateException e) {
                }

                innerContext.close();

                assertEquals(1, innerLangContext.disposeCalled);
                return null;
            }
        };
        eval(context, f);

        // ensure we are not yet closed
        eval(context, f);
        context.close();
    }

    @Test
    public void testTruffleContext() {
        Context context = Context.create();
        Function<Env, Object> f = new Function<Env, Object>() {
            public Object apply(Env env) {
                boolean assertions = false;
                assert (assertions = true) == true;
                if (!assertions) {
                    fail("Tests must be run with assertions on");
                }
                LanguageSPITestLanguage.runinside = null; // No more recursive runs inside
                Throwable[] error = new Throwable[1];
                Thread thread = new Thread(() -> {
                    try {
                        Source source = Source.newBuilder(LanguageSPITestLanguage.ID, "", "s").build();
                        boolean parsingFailed = false;
                        try {
                            // execute Truffle code in a fresh thread fails
                            env.parse(source).call();
                        } catch (AssertionError e) {
                            // No current context available.
                            parsingFailed = true;
                        }
                        if (!parsingFailed) {
                            fail("no assertion error \"No current context available.\"");
                        }

                        TruffleContext truffleContext = env.getContext();
                        // attach the Thread
                        Object prev = truffleContext.enter();
                        try {
                            // execute Truffle code
                            env.parse(source).call();
                        } finally {
                            // detach the Thread
                            truffleContext.leave(prev);
                        }
                    } catch (Throwable t) {
                        error[0] = t;
                    }
                });
                thread.start();
                try {
                    thread.join();
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                if (error[0] != null) {
                    throw new AssertionError(error[0]);
                }
                boolean leaveFailed = false;
                try {
                    TruffleContext truffleContext = env.getContext();
                    truffleContext.leave(null);
                } catch (AssertionError e) {
                    leaveFailed = true;
                }
                if (!leaveFailed) {
                    fail("no assertion error for leaving without enter");
                }
                return null;
            }
        };
        eval(context, f);
        context.close();
    }

    @Test
    public void testCloseInnerContextWithParent() {
        Context context = Context.create();
        LanguageContext returnedInnerContext = eval(context, new Function<Env, Object>() {
            public Object apply(Env env) {
                TruffleContext innerContext = env.newContextBuilder().build();
                Object p = innerContext.enter();
                LanguageContext innerLangContext = LanguageSPITestLanguage.getContext();
                innerContext.leave(p);
                return env.asGuestValue(innerLangContext);
            }
        }).asHostObject();
        context.close();
        // inner context automatically closed
        assertEquals(1, returnedInnerContext.disposeCalled);
    }

    @Test
    public void testParseOtherLanguage() {
        Context context = Context.newBuilder().build();
        eval(context, new Function<Env, Object>() {
            @SuppressWarnings("deprecation")
            public Object apply(Env t) {
                assertCorrectTarget(t.parse(Source.newBuilder("").language(ContextAPITestLanguage.ID).name("").build()));
                assertCorrectTarget(t.parse(Source.newBuilder("").mimeType(ContextAPITestLanguage.MIME).name("").build()));
                // this is here for compatibility because mime types and language ids were allowed
                // in between.
                assertCorrectTarget(t.parse(Source.newBuilder("").mimeType(ContextAPITestLanguage.ID).name("").build()));

                assertCorrectTarget(t.parse(Source.newBuilder(ContextAPITestLanguage.ID, "", "").name("").build()));
                assertCorrectTarget(t.parse(Source.newBuilder(ContextAPITestLanguage.ID, "", "").mimeType(ContextAPITestLanguage.MIME).name("").build()));
                // this is here for compatibility because mime types and language ids were allowed
                // in between.
                try {
                    t.parse(Source.newBuilder(ContextAPITestLanguage.ID, "", "").mimeType("text/invalid").build());
                    Assert.fail();
                } catch (IllegalArgumentException e) {
                    // illegal mime type
                }
                return null;
            }

            private void assertCorrectTarget(CallTarget target) {
                Assert.assertEquals(ContextAPITestLanguage.ID, ((RootCallTarget) target).getRootNode().getLanguageInfo().getId());
            }

        });
        context.close();
    }

    @Test
    public void testLazyInit() {
        LanguageSPITestLanguage.instanceCount.set(0);
        Context context = Context.create();
        assertEquals(0, LanguageSPITestLanguage.instanceCount.get());
        context.initialize(LanguageSPITestLanguage.ID);
        assertEquals(1, LanguageSPITestLanguage.instanceCount.get());
        context.close();
    }

    @Test
    public void testLazyInitSharedEngine() {
        LanguageSPITestLanguage.instanceCount.set(0);
        Engine engine = Engine.create();
        Context context1 = Context.newBuilder().engine(engine).build();
        Context context2 = Context.newBuilder().engine(engine).build();
        assertEquals(0, LanguageSPITestLanguage.instanceCount.get());
        context1.initialize(LanguageSPITestLanguage.ID);
        context2.initialize(LanguageSPITestLanguage.ID);
        assertEquals(1, LanguageSPITestLanguage.instanceCount.get());
        context1.close();
        context2.close();
    }

    @TruffleLanguage.Registration(id = OneContextLanguage.ID, name = OneContextLanguage.ID, version = "1.0", contextPolicy = ContextPolicy.EXCLUSIVE)
    public static class OneContextLanguage extends MultiContextLanguage {
        static final String ID = "OneContextLanguage";

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return null;
        }

        public static OneContextLanguage getCurrentLanguage() {
            return getCurrentLanguage(OneContextLanguage.class);
        }

    }

    @TruffleLanguage.Registration(id = MultiContextLanguage.ID, name = MultiContextLanguage.ID, version = "1.0", contextPolicy = ContextPolicy.SHARED)
    public static class MultiContextLanguage extends ProxyLanguage {

        static final String ID = "MultiContextLanguage";

        List<Env> createContextCalled = new ArrayList<>();
        List<Source> parseCalled = new ArrayList<>();
        List<Integer> initializeMultiContextCalled = new ArrayList<>(); // executionIndex
        List<Integer> initializeMultipleContextsCalled = new ArrayList<>(); // executionIndex
        boolean contextCachingEnabled = false;
        int executionIndex;

        @Option(help = "", category = OptionCategory.INTERNAL) static final OptionKey<Integer> DummyOption = new OptionKey<>(0);

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new MultiContextLanguageOptionDescriptors();
        }

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            return firstOptions.get(DummyOption).equals(newOptions.get(DummyOption));
        }

        @Override
        protected LanguageContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
            wrapper = false;
            executionIndex++;
            createContextCalled.add(env);
            return super.createContext(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            executionIndex++;
            parseCalled.add(request.getSource());
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
        }

        @Override
        protected void initializeMultipleContexts() {
            executionIndex++;
            initializeMultipleContextsCalled.add(executionIndex);
        }

        @Override
        protected boolean initializeMultiContext() {
            executionIndex++;
            initializeMultiContextCalled.add(executionIndex);
            return contextCachingEnabled;
        }

        static MultiContextLanguage getInstance() {
            return MultiContextLanguage.getCurrentLanguage(MultiContextLanguage.class);
        }

        static MultiContextLanguage getInstance(Context context) {
            context.enter();
            try {
                return MultiContextLanguage.getCurrentLanguage(MultiContextLanguage.class);
            } finally {
                context.leave();
            }
        }

    }

    @Test
    public void testMultiContextBoundEngine() {
        org.graalvm.polyglot.Source source1 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "foo");
        org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "bar");

        // test behavior with bound engine
        Context context = Context.create();

        context.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang = MultiContextLanguage.getInstance(context);

        assertEquals(1, lang.createContextCalled.size());
        assertTrue(lang.initializeMultiContextCalled.isEmpty());
        assertTrue(lang.parseCalled.isEmpty());

        context.eval(source1);
        assertEquals(1, lang.parseCalled.size());
        assertEquals(source1.getCharacters(), lang.parseCalled.get(0).getCharacters());

        context.eval(source1); // cached parse
        assertEquals(1, lang.parseCalled.size());
        assertTrue(lang.initializeMultiContextCalled.isEmpty());

        context.eval(source2); // cached parse
        assertEquals(2, lang.parseCalled.size());
        assertEquals(source2.getCharacters(), lang.parseCalled.get(1).getCharacters());
        assertTrue(lang.initializeMultiContextCalled.isEmpty());
        context.close();
    }

    @Test
    public void testMultiContextBoundEngineInnerContextWithCaching() throws Exception {

        org.graalvm.polyglot.Source source1 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "foo");
        org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "bar");

        Source truffleSource1 = getTruffleSource(source1);
        Source truffleSource2 = getTruffleSource(source2);

        // test behavior with bound engine
        Context context = Context.create();
        context.enter();

        context.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang = MultiContextLanguage.getInstance(context);

        assertEquals(1, lang.createContextCalled.size());
        assertTrue(lang.initializeMultiContextCalled.isEmpty());
        assertTrue(lang.parseCalled.isEmpty());
        Env env = lang.createContextCalled.get(0);

        context.eval(source1);
        assertEquals(1, lang.parseCalled.size());
        assertEquals(source1.getCharacters(), lang.parseCalled.get(0).getCharacters());

        TruffleContext innerContext = env.newContextBuilder().build();
        Object prev = innerContext.enter();
        Env innerEnv = ProxyLanguage.getCurrentContext().env;
        innerEnv.parse(truffleSource1);
        assertEquals(1, lang.parseCalled.size());
        assertEquals(1, lang.initializeMultiContextCalled.size());
        assertEquals(1, lang.initializeMultipleContextsCalled.size());
        assertEquals(3, (int) lang.initializeMultipleContextsCalled.get(0));
        assertEquals(4, (int) lang.initializeMultiContextCalled.get(0));
        assertEquals(2, lang.createContextCalled.size());

        innerEnv.parse(truffleSource1);
        assertEquals(1, lang.parseCalled.size());

        innerEnv.parse(truffleSource2);
        assertEquals(2, lang.parseCalled.size());

        innerContext.leave(prev);
        innerContext.close();

        context.eval(source2);
        assertEquals(2, lang.parseCalled.size());

        context.leave();
        context.close();
        assertEquals(1, lang.initializeMultiContextCalled.size());
        assertEquals(2, lang.createContextCalled.size());
    }

    @Test
    public void testMultiContextBoundEngineInnerContextNoCaching() throws Exception {
        org.graalvm.polyglot.Source source1 = org.graalvm.polyglot.Source.create(OneContextLanguage.ID, "foo");
        org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(OneContextLanguage.ID, "bar");

        Source truffleSource1 = getTruffleSource(source1);
        Source truffleSource2 = getTruffleSource(source2);

        // test behavior with bound engine
        Context context = Context.create();
        context.enter();

        context.initialize(OneContextLanguage.ID);
        MultiContextLanguage lang = OneContextLanguage.getInstance(context);
        assertEquals(1, lang.createContextCalled.size());
        assertTrue(lang.initializeMultiContextCalled.isEmpty());
        assertTrue(lang.parseCalled.isEmpty());
        Env env = lang.createContextCalled.get(0);

        context.eval(source1);
        assertEquals(1, lang.parseCalled.size());
        assertEquals(source1.getCharacters(), lang.parseCalled.get(0).getCharacters());

        TruffleContext innerContext = env.newContextBuilder().build();
        Object prev = innerContext.enter();

        MultiContextLanguage innerLang = OneContextLanguage.getCurrentLanguage();
        assertNotSame(innerLang, lang);

        Env innerEnv = innerLang.getContextReference().get().env;
        innerEnv.parse(truffleSource1);
        assertEquals(1, innerLang.parseCalled.size());
        assertEquals(0, innerLang.initializeMultiContextCalled.size());
        assertEquals(1, innerLang.createContextCalled.size());

        innerEnv.parse(truffleSource1);
        assertEquals(1, innerLang.parseCalled.size());

        innerEnv.parse(truffleSource2);
        assertEquals(2, innerLang.parseCalled.size());

        innerContext.leave(prev);
        innerContext.close();

        assertEquals(1, lang.parseCalled.size());
        context.eval(source2);
        assertEquals(2, lang.parseCalled.size());

        context.leave();
        context.close();

        assertEquals(1, lang.createContextCalled.size());
        assertEquals(0, lang.initializeMultiContextCalled.size());
    }

    private static Source getTruffleSource(org.graalvm.polyglot.Source source) throws NoSuchFieldException, IllegalAccessException {
        java.lang.reflect.Field impl = source.getClass().getDeclaredField("impl");
        impl.setAccessible(true);
        return (Source) impl.get(source);
    }

    @Test
    public void testInitializeMultiContextNotCalledForExclusive() {
        // test behavior with explicit engine
        Engine engine = Engine.create();
        Context context1 = Context.newBuilder().engine(engine).build();
        Context context2 = Context.newBuilder().engine(engine).build();
        context1.initialize(OneContextLanguage.ID);
        context2.initialize(OneContextLanguage.ID);
        MultiContextLanguage lang1 = OneContextLanguage.getInstance(context1);
        MultiContextLanguage lang2 = OneContextLanguage.getInstance(context2);
        assertEquals(0, lang1.initializeMultiContextCalled.size());
        assertEquals(0, lang2.initializeMultiContextCalled.size());
    }

    @Test
    public void testInitializeCalledWithEngineOptions() {
        Engine engine = Engine.newBuilder().option(MultiContextLanguage.ID + ".DummyOption", "42").build();
        Context context = Context.newBuilder().engine(engine).build();
        context.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang = MultiContextLanguage.getInstance(context);
        assertEquals(1, lang.initializeMultiContextCalled.size());
        assertEquals(1, lang.initializeMultipleContextsCalled.size());
        assertEquals(1, (int) lang.initializeMultipleContextsCalled.get(0));
        assertEquals(2, (int) lang.initializeMultiContextCalled.get(0));
        assertEquals(1, lang.createContextCalled.size());
        context.close();
        engine.close();
    }

    @Test
    public void testMultiContextExplicitEngineNoCaching() {
        org.graalvm.polyglot.Source source1 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "foo");
        org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "bar");

        // test behavior with explicit engine
        Engine engine = Engine.create();
        Context context1 = Context.newBuilder().engine(engine).build();

        context1.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang1 = MultiContextLanguage.getInstance(context1);

        assertTrue(lang1.parseCalled.isEmpty());
        assertEquals(1, lang1.initializeMultiContextCalled.size());
        assertEquals(1, lang1.initializeMultipleContextsCalled.size());
        assertEquals(1, (int) lang1.initializeMultipleContextsCalled.get(0));
        assertEquals(2, (int) lang1.initializeMultiContextCalled.get(0));
        assertEquals(1, lang1.createContextCalled.size());

        context1.eval(source1);
        assertEquals(1, lang1.parseCalled.size());
        assertEquals(1, lang1.createContextCalled.size());

        context1.eval(source1);
        assertEquals(1, lang1.parseCalled.size());
        assertEquals(1, lang1.createContextCalled.size());

        context1.eval(source2);
        assertEquals(2, lang1.parseCalled.size());
        assertEquals(1, lang1.createContextCalled.size());

        // pass a dummy option to avoid caching
        Context context2 = Context.newBuilder().engine(engine).option(MultiContextLanguage.ID + ".DummyOption", "42").build();
        context2.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang2 = MultiContextLanguage.getInstance(context2);

        assertEquals(2, lang1.parseCalled.size());
        assertEquals(0, lang2.parseCalled.size());
        assertEquals(1, lang1.createContextCalled.size());
        assertEquals(1, lang2.createContextCalled.size());

        context2.eval(source1);
        assertEquals(2, lang1.parseCalled.size());
        assertEquals(1, lang2.parseCalled.size());

        context2.eval(source1);
        assertEquals(2, lang1.parseCalled.size());
        assertEquals(1, lang2.parseCalled.size());

        context2.eval(source2);
        assertEquals(2, lang1.parseCalled.size());
        assertEquals(2, lang2.parseCalled.size());
        assertEquals(1, lang1.createContextCalled.size());
        assertEquals(1, lang2.createContextCalled.size());
        engine.close();

        assertEquals(1, lang1.initializeMultiContextCalled.size());
        assertEquals(1, lang1.initializeMultipleContextsCalled.size());
        assertEquals(1, (int) lang1.initializeMultipleContextsCalled.get(0));
        assertEquals(2, (int) lang1.initializeMultiContextCalled.get(0));
        assertEquals(1, lang1.createContextCalled.size());
        assertEquals(1, lang2.createContextCalled.size());
    }

    @Test
    public void testMultiContextExplicitEngineWithCaching() {
        org.graalvm.polyglot.Source source1 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "foo");
        org.graalvm.polyglot.Source source2 = org.graalvm.polyglot.Source.create(MultiContextLanguage.ID, "bar");

        // test behavior with explicit engine
        Engine engine = Engine.create();
        Context context1 = Context.newBuilder().engine(engine).build();

        context1.initialize(MultiContextLanguage.ID);
        MultiContextLanguage lang = MultiContextLanguage.getInstance(context1);

        assertTrue(lang.parseCalled.isEmpty());
        assertEquals(1, lang.initializeMultiContextCalled.size());
        assertEquals(1, lang.initializeMultipleContextsCalled.size());
        assertEquals(1, (int) lang.initializeMultipleContextsCalled.get(0));
        assertEquals(2, (int) lang.initializeMultiContextCalled.get(0));
        assertEquals(1, lang.createContextCalled.size());

        context1.eval(source1);
        assertEquals(1, lang.parseCalled.size());

        context1.eval(source1);
        assertEquals(1, lang.parseCalled.size());

        context1.eval(source2);
        assertEquals(2, lang.parseCalled.size());
        assertEquals(1, lang.createContextCalled.size());

        Context context2 = Context.newBuilder().engine(engine).build();

        context2.initialize(MultiContextLanguage.ID);
        assertEquals(2, lang.parseCalled.size());
        assertEquals(2, lang.createContextCalled.size());

        context2.eval(source1);
        assertEquals(2, lang.parseCalled.size());

        context2.eval(source1);
        assertEquals(2, lang.parseCalled.size());

        context2.eval(source2);
        assertEquals(2, lang.parseCalled.size());

        engine.close();

        assertEquals(2, lang.createContextCalled.size());
        assertEquals(1, lang.initializeMultiContextCalled.size());
        assertEquals(1, lang.initializeMultipleContextsCalled.size());
    }

    @Test
    public void testErrorInCreateContext() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected LanguageContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
                throw new RuntimeException();
            }
        });
        testFails((c) -> c.initialize(ProxyLanguage.ID));
    }

    @Test
    public void testErrorInInitializeContext() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected void initializeContext(LanguageContext context) throws Exception {
                throw new RuntimeException();
            }
        });
        testFails((c) -> c.initialize(ProxyLanguage.ID));
    }

    @Test
    public void testErrorInInitializeThread() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected void initializeThread(LanguageContext context, Thread thread) {
                throw new RuntimeException();
            }
        });
        testFails((c) -> c.initialize(ProxyLanguage.ID));
    }

    @Test
    public void testErrorInDisposeLanguage() {
        AtomicBoolean fail = new AtomicBoolean(true);
        ProxyLanguage.setDelegate(new ProxyLanguage() {

            @Override
            protected void disposeContext(LanguageContext context) {
                if (fail.get()) {
                    throw new RuntimeException();
                }
            }
        });

        Context c = Context.create();
        c.initialize(ProxyLanguage.ID);
        testFails(() -> {
            c.close();
        });
        testFails(() -> {
            c.close();
        });

        // clean up the context
        fail.set(false);
        c.close();
    }

    @Test
    public void testErrorInParse() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
                throw new RuntimeException();
            }
        });
        Context c = Context.create();
        c.initialize(ProxyLanguage.ID);
        testFails(() -> c.eval(ProxyLanguage.ID, "t0"));
        testFails(() -> c.eval(ProxyLanguage.ID, "t1"));
        c.close();
    }

    @Test
    public void testErrorInFindMetaObject() {
        final TruffleObject testObject = new TruffleObject() {
            @Override
            public ForeignAccess getForeignAccess() {
                return null;
            }
        };
        ProxyLanguage.setDelegate(new ProxyLanguage() {

            @Override
            protected Object findMetaObject(LanguageContext context, Object value) {
                throw new RuntimeException();
            }

            @Override
            protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(testObject));
            }

            @Override
            protected boolean isObjectOfLanguage(Object object) {
                return object == testObject;
            }
        });
        Context c = Context.create();
        Value v = c.eval(ProxyLanguage.ID, "");
        testFails(() -> v.getMetaObject());
        testFails(() -> v.getMetaObject());
        c.close();
    }

    @Test
    @SuppressWarnings("all")
    public void testLazyOptionInit() {
        AtomicInteger getOptionDescriptors = new AtomicInteger(0);
        AtomicInteger iterator = new AtomicInteger(0);
        AtomicInteger get = new AtomicInteger(0);
        ProxyLanguage.setDelegate(new ProxyLanguage() {

            final OptionKey<String> option = new OptionKey<>("");
            final List<OptionDescriptor> descriptors;
            {
                descriptors = new ArrayList<>();
                descriptors.add(OptionDescriptor.newBuilder(option, ProxyLanguage.ID + ".option").build());
            }

            @Override
            protected OptionDescriptors getOptionDescriptors() {
                getOptionDescriptors.incrementAndGet();
                return new OptionDescriptors() {

                    public Iterator<OptionDescriptor> iterator() {
                        iterator.incrementAndGet();
                        return descriptors.iterator();
                    }

                    public OptionDescriptor get(String optionName) {
                        get.incrementAndGet();
                        for (OptionDescriptor optionDescriptor : descriptors) {
                            if (optionDescriptor.getName().equals(optionName)) {
                                return optionDescriptor;
                            }
                        }
                        return null;
                    }
                };
            }
        });
        Context c = Context.create();
        c.close();
        assertEquals(0, getOptionDescriptors.get());
        assertEquals(0, iterator.get());
        assertEquals(0, get.get());

        c = Context.newBuilder(ProxyLanguage.ID).option(ProxyLanguage.ID + ".option", "foobar").build();
        assertEquals(1, getOptionDescriptors.get());
        assertEquals(1, get.get());
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        if (assertionsEnabled) {
            // with assertions enabled we assert the options.
            assertEquals(1, iterator.get());
        } else {
            // for valid options we should not need the iterator
            assertEquals(0, iterator.get());
        }
        c.close();

        // test lazyness when using meta-data
        getOptionDescriptors.set(0);
        iterator.set(0);
        get.set(0);

        c = Context.create();
        OptionDescriptors descriptors = c.getEngine().getLanguages().get(ProxyLanguage.ID).getOptions();
        assertEquals(1, getOptionDescriptors.get());
        if (assertionsEnabled) {
            // with assertions enabled we assert the options.
            assertEquals(1, iterator.get());
        } else {
            // for valid options we should not need the iterator
            assertEquals(0, iterator.get());
        }
        assertEquals(0, get.get());

        for (OptionDescriptor descriptor : descriptors) {
        }
        assertEquals(1, getOptionDescriptors.get());
        if (assertionsEnabled) {
            // with assertions enabled we assert the options.
            assertEquals(2, iterator.get());
        } else {
            // for valid options we should not need the iterator
            assertEquals(1, iterator.get());
        }
        assertEquals(0, get.get());

        assertNotNull(descriptors.get(ProxyLanguage.ID + ".option"));

        assertEquals(1, getOptionDescriptors.get());
        assertEquals(1, get.get());

        c.close();
    }

    @Test
    public void testExportSymbolInCreate() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected LanguageContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
                env.exportSymbol("symbol", env.asGuestValue(env));
                return super.createContext(env);
            }

        });
        Context c = Context.create();
        c.initialize(ProxyLanguage.ID);
        assertTrue(c.getPolyglotBindings().getMember("symbol").isHostObject());
        c.close();
    }

    @Test
    public void testCreateContextDuringDispose() {
        AtomicBoolean contextOnFinalize = new AtomicBoolean(true);
        AtomicBoolean contextOnDispose = new AtomicBoolean(false);
        ProxyLanguage.setDelegate(new ProxyLanguage() {

            @Override
            protected LanguageContext createContext(Env env) {
                return new LanguageContext(env);
            }

            @Override
            protected void finalizeContext(LanguageContext context) {
                if (contextOnFinalize.get()) {
                    context.env.newContextBuilder().build();
                }
            }

            @Override
            protected void disposeContext(LanguageContext context) {
                if (contextOnDispose.get()) {
                    context.env.newContextBuilder().build();
                }
            }
        });

        Context c = Context.create();
        c.initialize(ProxyLanguage.ID);
        // Fails on finalize
        testFails(() -> {
            c.close();
        });
        contextOnFinalize.set(false);
        contextOnDispose.set(true);
        // Fails on dispose
        testFails(() -> {
            c.close();
        });

        // clean up the context
        contextOnDispose.set(false);
        c.close();
    }

    @Test
    public void testCreateThreadDuringDispose() {
        AtomicBoolean contextOnFinalize = new AtomicBoolean(true);
        AtomicBoolean contextOnDispose = new AtomicBoolean(false);
        ProxyLanguage.setDelegate(new ProxyLanguage() {

            @Override
            protected LanguageContext createContext(Env env) {
                return new LanguageContext(env);
            }

            @Override
            protected void finalizeContext(LanguageContext context) {
                if (contextOnFinalize.get()) {
                    context.env.createThread(() -> {
                    }).start();
                }
            }

            @Override
            protected void disposeContext(LanguageContext context) {
                if (contextOnDispose.get()) {
                    context.env.createThread(() -> {
                    }).start();
                }
            }
        });

        Context c = Context.create();
        c.initialize(ProxyLanguage.ID);
        // Fails on finalize
        testFails(() -> {
            c.close();
        });
        contextOnFinalize.set(false);
        contextOnDispose.set(true);
        // Fails on dispose
        testFails(() -> {
            c.close();
        });

        // clean up the context
        contextOnDispose.set(false);
        c.close();
    }

    @Test
    public void testNullContext() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected LanguageContext createContext(TruffleLanguage.Env env) {
                return null;
            }
        });
        try (Engine engine = Engine.create()) {
            for (int i = 0; i < 2; i++) {
                try (Context context = Context.newBuilder(ProxyLanguage.ID).engine(engine).build()) {
                    context.initialize(ProxyLanguage.ID);
                }
            }
        }
    }

    @Test
    public void testExceptionGetSourceLocation() {
        try (Context context = Context.create(LanguageSPITestLanguage.ID)) {
            final String text = "0123456789";
            LanguageSPITestLanguage.runinside = (env) -> {
                Source src = Source.newBuilder(LanguageSPITestLanguage.ID, text, "test.txt").build();
                throw new ParseException(src, 1, 2);
            };
            try {
                context.eval(LanguageSPITestLanguage.ID, text);
                Assert.fail("PolyglotException expected.");
            } catch (PolyglotException pe) {
                Assert.assertTrue(pe.isSyntaxError());
                Assert.assertEquals("12", pe.getSourceLocation().getCharacters().toString());
            } finally {
                LanguageSPITestLanguage.runinside = null;
            }
        }
    }

    private static void testFails(Runnable consumer) {
        try {
            consumer.run();
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.isInternalError());
            assertFalse(e.isHostException()); // should not expose internal errors
        }
    }

    private static void testFails(Consumer<Context> consumer) {
        Context context = Context.create();
        testFails(() -> consumer.accept(context));
        context.close();
    }

    private int findScopeInvokes = 0;

    private void setupTopScopes(Object... scopesArray) {
        findScopeInvokes = 0;
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected Iterable<Scope> findTopScopes(LanguageContext context) {
                findScopeInvokes++;
                List<Scope> scopes = new ArrayList<>();
                for (int i = 0; i < scopesArray.length; i++) {
                    Object scope = scopesArray[i];
                    scopes.add(Scope.newBuilder(String.valueOf(i), scope).build());
                }
                return scopes;
            }
        });
    }

    @Test
    public void testBindingsWithInvalidScopes() {
        setupTopScopes(new ProxyInteropObject() {
        });
        Context c = Context.create();
        assertEquals(0, findScopeInvokes);
        testFails(() -> c.getBindings(ProxyLanguage.ID));
        assertEquals(1, findScopeInvokes);
        c.close();
    }

    private static class TestScope extends ProxyInteropObject {

        final Map<String, Object> values = new HashMap<>();
        boolean modifiable;
        boolean insertable;
        boolean removable;

        @Override
        public boolean hasKeys() {
            return true;
        }

        @Override
        public Object keys() throws UnsupportedMessageException {
            return new ProxyInteropObject() {

                private final String[] keys = values.keySet().toArray(new String[0]);

                @Override
                public boolean hasSize() {
                    return true;
                }

                @Override
                public int getSize() {
                    return keys.length;
                }

                @Override
                public Object read(Number key) throws UnsupportedMessageException, UnknownIdentifierException {
                    return keys[key.intValue()];
                }

                @Override
                public int keyInfo(Number key) {
                    return (key.intValue() < keys.length && key.intValue() >= 0) ? KeyInfo.READABLE : KeyInfo.NONE;
                }

            };
        }

        @Override
        public Object read(String key) throws UnsupportedMessageException, UnknownIdentifierException {
            if (values.containsKey(key)) {
                return values.get(key);
            } else {
                throw UnknownIdentifierException.raise(key);
            }
        }

        @Override
        public Object write(String key, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
            if (modifiable && values.containsKey(key)) {
                values.put(key, value);
                return value;
            }
            if (insertable && !values.containsKey(key)) {
                values.put(key, value);
                return value;
            }
            throw UnsupportedMessageException.raise(Message.WRITE);
        }

        @Override
        public boolean remove(String key) throws UnsupportedMessageException, UnknownIdentifierException {
            if (removable) {
                if (values.containsKey(key)) {
                    values.remove(key);
                    return true;
                } else {
                    return false;
                }
            }
            return super.remove(key);
        }

        @Override
        public int keyInfo(String key) {
            int keyInfo = KeyInfo.NONE;
            if (values.containsKey(key)) {
                keyInfo |= KeyInfo.READABLE;
                if (modifiable) {
                    keyInfo |= KeyInfo.MODIFIABLE;
                }
                if (removable) {
                    keyInfo |= KeyInfo.REMOVABLE;
                }
            } else {
                if (insertable) {
                    keyInfo |= KeyInfo.INSERTABLE;
                }
            }
            return keyInfo;
        }
    }

    @Test
    public void testBindingsWithDefaultScope() {
        Context c = Context.create();
        Value bindings = c.getBindings(ProxyLanguage.ID);
        assertTrue(bindings.hasMembers());
        assertFalse(bindings.hasMember(""));
        assertTrue(bindings.getMemberKeys().isEmpty());
        assertNull(bindings.getMember(""));
        ValueAssert.assertFails(() -> bindings.putMember("", ""), UnsupportedOperationException.class);
        assertFalse(bindings.removeMember(""));
        assertValue(bindings);

        c.close();
    }

    @Test
    public void testBindingsWithSimpleScope() {
        TestScope scope = new TestScope();
        setupTopScopes(scope);
        Context c = Context.create();
        assertEquals(0, findScopeInvokes);
        Value bindings = c.getBindings(ProxyLanguage.ID);
        c.getBindings(ProxyLanguage.ID);
        assertEquals(1, findScopeInvokes);

        scope.values.put("foobar", "baz");

        assertTrue(bindings.hasMembers());
        assertFalse(bindings.hasMember(""));
        assertTrue(bindings.hasMember("foobar"));
        assertEquals(new HashSet<>(Arrays.asList("foobar")), bindings.getMemberKeys());
        assertNull(bindings.getMember(""));
        assertEquals("baz", bindings.getMember("foobar").asString());
        ValueAssert.assertFails(() -> bindings.putMember("", ""), UnsupportedOperationException.class);
        assertFalse(bindings.removeMember(""));
        ValueAssert.assertFails(() -> bindings.removeMember("foobar"), UnsupportedOperationException.class);
        assertValue(bindings, ValueAssert.Trait.MEMBERS);

        scope.insertable = true;
        bindings.putMember("baz", "val");
        assertEquals("val", scope.values.get("baz"));
        assertEquals("val", bindings.getMember("baz").asString());
        assertFails(() -> bindings.putMember("foobar", "42"), UnsupportedOperationException.class);
        assertValue(bindings, ValueAssert.Trait.MEMBERS);

        scope.modifiable = true;
        bindings.putMember("foobar", "val");
        assertEquals("val", scope.values.get("foobar"));
        assertEquals("val", bindings.getMember("foobar").asString());
        assertValue(bindings, ValueAssert.Trait.MEMBERS);

        scope.removable = true;
        assertFalse(bindings.removeMember(""));
        assertTrue(bindings.removeMember("foobar"));
        assertValue(bindings, ValueAssert.Trait.MEMBERS);

        assertEquals(1, findScopeInvokes);

        c.close();
    }

    @Test
    public void testBindingsWithMultipleScopes() {
        // innermost to outermost
        TestScope[] scopes = new TestScope[5];
        for (int i = 0; i < 5; i++) {
            scopes[i] = new TestScope();
        }
        setupTopScopes((Object[]) scopes);

        Context c = Context.create();

        assertEquals(0, findScopeInvokes);
        Value bindings = c.getBindings(ProxyLanguage.ID);
        assertEquals(1, findScopeInvokes);

        assertTrue(bindings.hasMembers());
        assertFalse(bindings.hasMember(""));
        assertNull(bindings.getMember(""));

        ValueAssert.assertFails(() -> bindings.putMember("foo", "bar"), UnsupportedOperationException.class);

        // test insertion into first insertable scope
        scopes[1].insertable = true;
        scopes[2].insertable = true;
        bindings.putMember("foo", "bar"); // should end up in scope 1
        assertEquals("bar", bindings.getMember("foo").asString());
        assertEquals("bar", scopes[1].values.get("foo"));
        assertNull(scopes[0].values.get("foo"));
        assertNull(scopes[2].values.get("foo"));
        ValueAssert.assertValue(bindings, ValueAssert.Trait.MEMBERS);

        // test check for existing keys for remove
        scopes[2].removable = true;
        scopes[2].values.put("foo", "baz");
        scopes[2].values.put("bar", "baz");
        scopes[3].values.put("bar", "val");
        assertEquals("bar", bindings.getMember("foo").asString());
        assertEquals("baz", bindings.getMember("bar").asString());
        ValueAssert.assertFails(() -> bindings.removeMember("foo"), UnsupportedOperationException.class);
        assertTrue(bindings.removeMember("bar"));
        assertNotNull(scopes[2].values.get("foo"));
        assertNull(scopes[2].values.get("bar"));
        assertEquals("val", bindings.getMember("bar").asString());
        assertValue(bindings, ValueAssert.Trait.MEMBERS);

        c.close();
    }

    @Test
    public void testPolyglotBindings() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        return getCurrentContext(ProxyLanguage.class).env.getPolyglotBindings();
                    }
                });
            }
        });

        Context c = Context.create();
        Value languageBindings = c.eval(ProxyLanguage.ID, "");
        Value polyglotBindings = c.getPolyglotBindings();

        polyglotBindings.putMember("foo", "bar");
        assertEquals("bar", polyglotBindings.getMember("foo").asString());
        assertEquals("bar", languageBindings.getMember("foo").asString());

        languageBindings.putMember("baz", "val");
        assertEquals("val", polyglotBindings.getMember("baz").asString());
        assertEquals("val", languageBindings.getMember("baz").asString());

        assertValue(polyglotBindings);
        assertValue(languageBindings);

        c.close();
    }

    @Test
    public void testPolyglotBindingsMultiThreaded() throws Throwable {
        ProxyLanguage.setDelegate(new ProxyLanguage() {

            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }

            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        return getCurrentContext(ProxyLanguage.class).env.getPolyglotBindings();
                    }
                });
            }
        });

        Context c = Context.create();
        ExecutorService service = Executors.newFixedThreadPool(20);

        Value languageBindings = c.eval(ProxyLanguage.ID, "");
        Value polyglotBindings = c.getPolyglotBindings();

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 2000; i++) {
            futures.add(service.submit(() -> {
                polyglotBindings.putMember("foo", "bar");
                assertEquals("bar", polyglotBindings.getMember("foo").asString());
                assertEquals("bar", languageBindings.getMember("foo").asString());

                languageBindings.putMember("baz", "42");
                assertEquals("42", polyglotBindings.getMember("baz").asString());
                assertEquals("42", languageBindings.getMember("baz").asString());
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get(100000, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        }

        service.shutdown();
        service.awaitTermination(100000, TimeUnit.MILLISECONDS);

        c.close();
    }

    @Test
    public void testPolyglotBindingsPreserveLanguage() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        Object bindings = getCurrentContext(ProxyLanguage.class).env.getPolyglotBindings();
                        try {
                            boundary((TruffleObject) bindings);
                        } catch (UnknownIdentifierException | UnsupportedTypeException | UnsupportedMessageException e) {
                            throw new AssertionError(e);
                        }
                        return bindings;
                    }

                    @CompilerDirectives.TruffleBoundary
                    private void boundary(TruffleObject bindings) throws UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException {
                        ForeignAccess.sendWrite(Message.WRITE.createNode(), bindings, "exportedValue", "convertOnToString");
                    }
                });
            }

            @Override
            protected String toString(LanguageContext context, Object value) {
                if (value.equals("convertOnToString")) {
                    return "myStringToString";
                }
                return super.toString(context, value);
            }
        });
        Context c = Context.create();
        c.eval(ProxyLanguage.ID, "");

        assertEquals("Make sure language specific toString was invoked.", "myStringToString", c.getPolyglotBindings().getMember("exportedValue").toString());
    }

    @Test
    public void testFindSourceLocation() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(new SourceHolder(request.getSource())));
            }

            @Override
            protected SourceSection findSourceLocation(ProxyLanguage.LanguageContext context, Object value) {
                if (value instanceof SourceHolder) {
                    final Source src = ((SourceHolder) value).source;
                    return src.createSection(0, src.getLength());
                }
                return super.findSourceLocation(context, value);
            }

            @Override
            protected boolean isObjectOfLanguage(Object object) {
                return object instanceof SourceHolder;
            }
        });

        try (Context context = Context.create(ProxyLanguage.ID)) {
            String text = "01234567";
            Value res = context.eval(ProxyLanguage.ID, text);
            assertNotNull(res);
            org.graalvm.polyglot.SourceSection sourceSection = res.getSourceLocation();
            assertNotNull(sourceSection);
            assertTrue(text.contentEquals(sourceSection.getCharacters()));
            res = context.asValue(new SourceHolder(Source.newBuilder(ProxyLanguage.ID, text, null).build()));
            sourceSection = res.getSourceLocation();
            assertNotNull(sourceSection);
            assertTrue(text.contentEquals(sourceSection.getCharacters()));
        }
    }

    @Test
    public void testToString() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(new SourceHolder(request.getSource())));
            }

            @Override
            protected String toString(ProxyLanguage.LanguageContext context, Object value) {
                if (value instanceof SourceHolder) {
                    final Source src = ((SourceHolder) value).source;
                    return src.getCharacters().toString();
                }
                return super.toString(context, value);
            }

            @Override
            protected boolean isObjectOfLanguage(Object object) {
                return object instanceof SourceHolder;
            }
        });

        try (Context context = Context.create(ProxyLanguage.ID)) {
            String text = "01234567";
            Value res = context.eval(ProxyLanguage.ID, text);
            assertNotNull(res);
            String toString = res.toString();
            assertEquals(text, toString);
            res = context.asValue(new SourceHolder(Source.newBuilder(ProxyLanguage.ID, text, null).build()));
            toString = res.toString();
            assertEquals(text, toString);
        }
    }

    @Test
    public void testLookup() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
                Env env = ProxyLanguage.getCurrentContext().env;
                LanguageInfo languageInfo = env.getLanguages().get(LanguageSPITestLanguage.ID);
                boolean found = env.lookup(languageInfo, LanguageSPITestLanguageService.class) != null;
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(found));
            }
        });
        // Not loaded language
        try (Context context = Context.create(LanguageSPITestLanguage.ID, ProxyLanguage.ID)) {
            Value result = context.eval(ProxyLanguage.ID, "");
            assertTrue(result.isBoolean());
            assertFalse(result.asBoolean());
        }
        // Loaded language
        try (Context context = Context.create(LanguageSPITestLanguage.ID, ProxyLanguage.ID)) {
            context.initialize(LanguageSPITestLanguage.ID);
            Value result = context.eval(ProxyLanguage.ID, "");
            assertTrue(result.isBoolean());
            assertTrue(result.asBoolean());
        }
    }

    static final String INHERITED_VERSION = "SPIInheritedVersionLanguage";

    @TruffleLanguage.Registration(id = INHERITED_VERSION, name = "")
    public static class InheritedVersionLanguage extends ProxyLanguage {
    }

    @Test
    public void testInheritedVersionLanguage() {
        Context context = Context.create();
        context.initialize(INHERITED_VERSION);
        final Engine engine = context.getEngine();
        assertEquals(engine.getVersion(), engine.getLanguages().get(INHERITED_VERSION).getVersion());
        context.close();
    }

    private static class SourceHolder implements TruffleObject {
        final Source source;

        SourceHolder(final Source source) {
            Objects.requireNonNull(source, "The source must be non null.");
            this.source = source;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return null;
        }
    }

    static final Source TEST_SOURCE = Source.newBuilder("", "", "testLanguageErrorDuringInitialization").build();

    @SuppressWarnings("serial")
    static class TestError extends RuntimeException implements TruffleException {

        public SourceSection getSourceLocation() {
            return TEST_SOURCE.createSection(0, 0);
        }

        public Node getLocation() {
            return null;
        }

    }

    @Test
    public void testLanguageErrorDuringInitialization() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected void initializeContext(LanguageContext c) throws Exception {
                throw new TestError();
            }
        });

        Context context = Context.create();
        try {
            context.eval(ProxyLanguage.ID, "");
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.isGuestException());
            assertEquals("testLanguageErrorDuringInitialization", e.getSourceLocation().getSource().getName());
        }
    }

}
