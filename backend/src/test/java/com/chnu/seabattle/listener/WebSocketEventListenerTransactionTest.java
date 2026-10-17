package com.chnu.seabattle.listener;

import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.dto.ws.MatchmakingStatusResponse;
import com.chnu.seabattle.dto.ws.MoveMessage;
import com.chnu.seabattle.dto.ws.PresenceEventType;
import com.chnu.seabattle.dto.ws.PresenceMessage;
import com.chnu.seabattle.dto.ws.RematchMessage;
import com.chnu.seabattle.dto.ws.WebSocketEvent;
import com.chnu.seabattle.entity.MoveResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Real Spring context (no mocks of the event machinery): checks that events published inside a
 * transaction are delivered only after commit, and events published without one are not dropped.
 */
@DisplayName("WebSocketEventListener transactional delivery")
class WebSocketEventListenerTransactionTest {

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SimpMessagingTemplate messagingTemplate() {
            return mock(SimpMessagingTemplate.class);
        }

        @Bean
        WebSocketEventListener webSocketEventListener(SimpMessagingTemplate template) {
            return new WebSocketEventListener(template);
        }
    }

    private AnnotationConfigApplicationContext context;
    private ApplicationEventPublisher publisher;
    private SimpMessagingTemplate template;
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        publisher = context;
        template = context.getBean(SimpMessagingTemplate.class);
        tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    private static Stream<WebSocketEvent> allKindsOfEvents() {
        return Stream.of(
                new PresenceMessage("t1", UUID.randomUUID(), PresenceEventType.OPPONENT_READY),
                new RematchMessage("t1", UUID.randomUUID(), PresenceEventType.REMATCH_AGREED, "t2"),
                new MatchmakingStatusResponse("u1", null, "t1"),
                new MoveMessage(UUID.randomUUID(), "t1",
                        List.of(new MoveResponse(0, 0, MoveResult.MISS)), UUID.randomUUID())
        );
    }

    private void verifyDelivered(WebSocketEvent event) {
        if (event instanceof MoveMessage move) {
            verify(template).convertAndSendToUser(move.recipient(), move.destination(), event);
        } else {
            verify(template).convertAndSend(event.destination(), (Object) event);
        }
    }

    private void verifyNothingDelivered() {
        verify(template, never()).convertAndSend(anyString(), any(Object.class));
        verify(template, never()).convertAndSendToUser(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Events published without a transaction are still delivered")
    void deliveredWhenNoTransaction() {
        allKindsOfEvents().forEach(event -> {
            Mockito.clearInvocations(template);

            publisher.publishEvent(event);

            verifyDelivered(event);
        });
    }

    @Test
    @DisplayName("Events published in a transaction are held until commit")
    void deliveredOnlyAfterCommit() {
        allKindsOfEvents().forEach(event -> {
            Mockito.clearInvocations(template);

            tx.executeWithoutResult(status -> {
                publisher.publishEvent(event);
                verifyNothingDelivered();
            });

            verifyDelivered(event);
        });
    }

    @Test
    @DisplayName("Events published in a transaction that rolls back are never delivered")
    void notDeliveredOnRollback() {
        allKindsOfEvents().forEach(event -> {
            Mockito.clearInvocations(template);

            tx.executeWithoutResult(status -> {
                publisher.publishEvent(event);
                status.setRollbackOnly();
            });

            verifyNothingDelivered();
        });
    }

    @Test
    @DisplayName("A failure after publishing rolls back and nothing is delivered")
    void notDeliveredWhenTransactionBodyThrows() {
        Supplier<RuntimeException> boom = () -> new IllegalStateException("redis down");

        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            publisher.publishEvent(new PresenceMessage("t1", UUID.randomUUID(), PresenceEventType.OPPONENT_READY));
            throw boom.get();
        }));

        verifyNothingDelivered();
    }
}
