/*
 * Copyright (c) 2010-2019. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.micrometer;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link MessageTimerMonitor}.
 *
 * @author Marijn van Zelst
 */
class MessageTimerMonitorTest {

    private static final String METER_NAME_PREFIX = "some-prefix";

    private MockClock mockedClock;
    private SimpleMeterRegistry meterRegistry;
    private MessageTimerMonitor.Builder testSubjectBuilder;

    @BeforeEach
    void setUp() {
        mockedClock = new MockClock();
        meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, mockedClock);
        testSubjectBuilder = MessageTimerMonitor.builder()
                                                .meterNamePrefix(METER_NAME_PREFIX)
                                                .meterRegistry(meterRegistry)
                                                .clock(mockedClock);
    }

    @Test
    void testMessagesWithoutTags() {
        MessageTimerMonitor testSubject = testSubjectBuilder.build();

        EventMessage<Object> foo = asEventMessage(1);
        EventMessage<Object> bar = asEventMessage("bar");
        EventMessage<Object> baz = asEventMessage("baz");

        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks =
                testSubject.onMessagesIngested(Arrays.asList(foo, bar, baz));

        mockedClock.addSeconds(1);
        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);
        callbacks.get(baz).reportIgnored();

        Timer all = requireNonNull(meterRegistry.find(METER_NAME_PREFIX + ".allTimer").timer());
        Timer successTimer = requireNonNull(meterRegistry.find(METER_NAME_PREFIX + ".successTimer").timer());
        Timer failureTimer = requireNonNull(meterRegistry.find(METER_NAME_PREFIX + ".failureTimer").timer());
        Timer ignoredTimer = requireNonNull(meterRegistry.find(METER_NAME_PREFIX + ".ignoredTimer").timer());

        assertEquals(3, all.totalTime(TimeUnit.SECONDS), 0);
        assertEquals(1, successTimer.totalTime(TimeUnit.SECONDS), 0);
        assertEquals(1, failureTimer.totalTime(TimeUnit.SECONDS), 0);
        assertEquals(1, ignoredTimer.totalTime(TimeUnit.SECONDS), 0);
    }

    @Test
    void testMessagesWithPayloadTypeAsCustomTag() {
        MessageTimerMonitor testSubject = testSubjectBuilder.tagsBuilder(
                message -> Tags.of(TagsUtil.PAYLOAD_TYPE_TAG, message.getPayloadType().getSimpleName())
        ).build();

        EventMessage<Object> foo = asEventMessage(1);
        EventMessage<Object> bar = asEventMessage("bar");
        EventMessage<Object> baz = asEventMessage("baz");

        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks =
                testSubject.onMessagesIngested(Arrays.asList(foo, bar, baz));

        mockedClock.addSeconds(1);
        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);
        callbacks.get(baz).reportIgnored();

        Collection<Timer> all = meterRegistry.find(METER_NAME_PREFIX + ".allTimer").timers();
        Collection<Timer> successTimer = meterRegistry.find(METER_NAME_PREFIX + ".successTimer").timers();
        Collection<Timer> failureTimer = meterRegistry.find(METER_NAME_PREFIX + ".failureTimer").timers();
        Collection<Timer> ignoredTimer = meterRegistry.find(METER_NAME_PREFIX + ".ignoredTimer").timers();

        // Expecting two timers with the same meter name ([name=PROCESSOR_NAME.suffix ; payloadType=Integer] , [name=PROCESSOR_NAME.suffix ; payloadType=String])
        assertEquals(2, all.size(), 0);
        assertEquals(2, successTimer.size(), 0);
        assertEquals(2, failureTimer.size(), 0);
        assertEquals(2, ignoredTimer.size(), 0);

        assertTrue(all.stream()
                      .filter(timer -> Objects.equals(timer.getId().getTag("payloadType"), "Integer"))
                      .allMatch(timer -> timer.totalTime(TimeUnit.SECONDS) == 1));
        assertTrue(all.stream()
                      .filter(timer -> Objects.equals(timer.getId().getTag("payloadType"), "String"))
                      .allMatch(timer -> (timer.totalTime(TimeUnit.SECONDS) == 2 && timer.max(TimeUnit.SECONDS) == 1)));

        assertTrue(successTimer.stream()
                               .filter(timer -> Objects.equals(timer.getId().getTag("payloadType"), "Integer"))
                               .allMatch(timer -> timer.totalTime(TimeUnit.SECONDS) == 1));

        assertTrue(successTimer.stream()
                               .filter(timer -> Objects.equals(timer.getId().getTag("payloadType"), "String"))
                               .allMatch(timer -> timer.totalTime(TimeUnit.SECONDS) == 0));

        assertTrue(failureTimer.stream()
                               .filter(timer -> Objects.equals(timer.getId().getTag("payloadType"), "Integer"))
                               .allMatch(timer -> timer.totalTime(TimeUnit.SECONDS) == 0));

        assertTrue(failureTimer.stream()
                               .filter(timer -> Objects.equals(timer.getId().getTag("payloadType"), "String"))
                               .allMatch(timer -> timer.totalTime(TimeUnit.SECONDS) == 1));
    }

    @Test
    void testMessagesWithMetadataAsCustomTag() {
        MessageTimerMonitor testSubject = testSubjectBuilder.tagsBuilder(
                message -> Tags.of("myMetaData", message.getMetaData().get("myMetadataKey").toString())
        ).build();

        EventMessage<Object> foo = asEventMessage("foo").withMetaData(Collections.singletonMap("myMetadataKey",
                                                                                               "myMetadataValue1"));
        EventMessage<Object> bar = asEventMessage("bar").withMetaData(Collections.singletonMap("myMetadataKey",
                                                                                               "myMetadataValue2"));
        EventMessage<Object> baz = asEventMessage("baz").withMetaData(Collections.singletonMap("myMetadataKey",
                                                                                               "myMetadataValue2"));

        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks =
                testSubject.onMessagesIngested(Arrays.asList(foo, bar, baz));

        mockedClock.addSeconds(1);
        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);
        callbacks.get(baz).reportIgnored();

        Collection<Timer> all = meterRegistry.find(METER_NAME_PREFIX + ".allTimer").timers();
        Collection<Timer> successTimer = meterRegistry.find(METER_NAME_PREFIX + ".successTimer").timers();
        Collection<Timer> failureTimer = meterRegistry.find(METER_NAME_PREFIX + ".failureTimer").timers();
        Collection<Timer> ignoredTimer = meterRegistry.find(METER_NAME_PREFIX + ".ignoredTimer").timers();

        // Expecting two timers with the same meter name ([name=PROCESSOR_NAME.suffix ; payloadType=Integer] , [name=PROCESSOR_NAME.suffix ; payloadType=String])
        assertEquals(2, all.size(), 0);
        assertEquals(2, successTimer.size(), 0);
        assertEquals(2, failureTimer.size(), 0);
        assertEquals(2, ignoredTimer.size(), 0);

        assertTrue(all.stream()
                      .filter(timer -> Objects.equals(timer.getId().getTag("myMetaData"), "myMetadataValue1"))
                      .allMatch(timer -> timer.totalTime(TimeUnit.SECONDS) == 1));
        assertTrue(all.stream()
                      .filter(timer -> Objects.equals(timer.getId().getTag("myMetaData"), "myMetadataValue2"))
                      .allMatch(timer -> (timer.totalTime(TimeUnit.SECONDS) == 2 && timer.max(TimeUnit.SECONDS) == 1)));

        assertTrue(successTimer.stream()
                               .filter(timer -> Objects.equals(timer.getId().getTag("myMetaData"), "myMetadataValue1"))
                               .allMatch(timer -> timer.totalTime(TimeUnit.SECONDS) == 1));

        assertTrue(successTimer.stream()
                               .filter(timer -> Objects.equals(timer.getId().getTag("myMetaData"), "myMetadataValue2"))
                               .allMatch(timer -> timer.totalTime(TimeUnit.SECONDS) == 0));

        assertTrue(failureTimer.stream()
                               .filter(timer -> Objects.equals(timer.getId().getTag("myMetaData"), "myMetadataValue1"))
                               .allMatch(timer -> timer.totalTime(TimeUnit.SECONDS) == 0));

        assertTrue(failureTimer.stream()
                               .filter(timer -> Objects.equals(timer.getId().getTag("myMetaData"), "myMetadataValue2"))
                               .allMatch(timer -> timer.totalTime(TimeUnit.SECONDS) == 1));
    }

    @Test
    void testBuildWithNullMeterNamePrefixThrowsAxonConfigurationException() {
        MessageTimerMonitor.Builder testSubject = MessageTimerMonitor.builder();
        assertThrows(AxonConfigurationException.class, () -> testSubject.meterNamePrefix(null));
    }

    @Test
    void testBuildWithEmptyMeterNamePrefixThrowsAxonConfigurationException() {
        MessageTimerMonitor.Builder testSubject = MessageTimerMonitor.builder();
        assertThrows(AxonConfigurationException.class, () -> testSubject.meterNamePrefix(""));
    }

    @Test
    void testBuildWithoutMeterNamePrefixThrowsAxonConfigurationException() {
        MessageTimerMonitor.Builder testSubject = MessageTimerMonitor.builder()
                                                                     .meterRegistry(meterRegistry);
        assertThrows(AxonConfigurationException.class, testSubject::build);
    }

    @Test
    void testBuildWithNullMeterRegistryThrowsAxonConfigurationException() {
        MessageTimerMonitor.Builder testSubject = MessageTimerMonitor.builder();
        assertThrows(AxonConfigurationException.class, () -> testSubject.meterRegistry(null));
    }

    @Test
    void testBuildWithoutMeterRegistryThrowsAxonConfigurationException() {
        MessageTimerMonitor.Builder testSubject = MessageTimerMonitor.builder()
                                                                     .meterNamePrefix(METER_NAME_PREFIX);
        assertThrows(AxonConfigurationException.class, testSubject::build);
    }

    @Test
    void testBuildWithNullClockThrowsAxonConfigurationException() {
        MessageTimerMonitor.Builder testSubject = MessageTimerMonitor.builder();
        assertThrows(AxonConfigurationException.class, () -> testSubject.clock(null));
    }

    @Test
    void testBuildWithNullTagsBuilderThrowsAxonConfigurationException() {
        MessageTimerMonitor.Builder testSubject = MessageTimerMonitor.builder();
        assertThrows(AxonConfigurationException.class, () -> testSubject.tagsBuilder(null));
    }

    @Test
    void testBuildWithNullTimerCustomizationThrowsAxonConfigurationException() {
        MessageTimerMonitor.Builder testSubject = MessageTimerMonitor.builder();
        assertThrows(AxonConfigurationException.class, () -> testSubject.timerCustomization(null));
    }
}
