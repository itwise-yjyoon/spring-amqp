package org.springframework.amqp.rabbit.connection;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.amqp.utils.test.TestUtils;
import org.springframework.beans.DirectFieldAccessor;

import com.rabbitmq.client.ConnectionFactory;

/**
 * @author Dave Syer
 */
public abstract class AbstractConnectionFactoryTests {

	protected abstract AbstractConnectionFactory createConnectionFactory(ConnectionFactory mockConnectionFactory);

	@Test
	public void testWithListener() throws IOException {

		com.rabbitmq.client.ConnectionFactory mockConnectionFactory = mock(com.rabbitmq.client.ConnectionFactory.class);
		com.rabbitmq.client.Connection mockConnection = mock(com.rabbitmq.client.Connection.class);

		when(mockConnectionFactory.newConnection((ExecutorService) null)).thenReturn(mockConnection);

		final AtomicInteger called = new AtomicInteger(0);
		AbstractConnectionFactory connectionFactory = createConnectionFactory(mockConnectionFactory);
		connectionFactory.setConnectionListeners(Arrays.asList(new ConnectionListener() {
			@Override
			public void onCreate(Connection connection) {
				called.incrementAndGet();
			}
			@Override
			public void onClose(Connection connection) {
				called.decrementAndGet();
			}
		}));

		Log logger = spy(TestUtils.getPropertyValue(connectionFactory, "logger", Log.class));
		when(logger.isInfoEnabled()).thenReturn(true);
		new DirectFieldAccessor(connectionFactory).setPropertyValue("logger", logger);
		Connection con = connectionFactory.createConnection();
		assertEquals(1, called.get());
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(logger).info(captor.capture());
		assertThat(captor.getValue(), containsString("Created new connection: SimpleConnection"));

		con.close();
		assertEquals(1, called.get());
		verify(mockConnection, never()).close(anyInt());

		connectionFactory.createConnection();
		assertEquals(1, called.get());

		connectionFactory.destroy();
		assertEquals(0, called.get());
		verify(mockConnection, atLeastOnce()).close(anyInt());

		verify(mockConnectionFactory, times(1)).newConnection((ExecutorService) null);

	}

	@Test
	public void testWithListenerRegisteredAfterOpen() throws IOException {

		com.rabbitmq.client.ConnectionFactory mockConnectionFactory = mock(com.rabbitmq.client.ConnectionFactory.class);
		com.rabbitmq.client.Connection mockConnection = mock(com.rabbitmq.client.Connection.class);

		when(mockConnectionFactory.newConnection((ExecutorService) null)).thenReturn(mockConnection);

		final AtomicInteger called = new AtomicInteger(0);
		AbstractConnectionFactory connectionFactory = createConnectionFactory(mockConnectionFactory);
		Connection con = connectionFactory.createConnection();
		assertEquals(0, called.get());

		connectionFactory.setConnectionListeners(Arrays.asList(new ConnectionListener() {
			@Override
			public void onCreate(Connection connection) {
				called.incrementAndGet();
			}
			@Override
			public void onClose(Connection connection) {
				called.decrementAndGet();
			}
		}));
		assertEquals(1, called.get());

		con.close();
		assertEquals(1, called.get());
		verify(mockConnection, never()).close(anyInt());

		connectionFactory.createConnection();
		assertEquals(1, called.get());

		connectionFactory.destroy();
		assertEquals(0, called.get());
		verify(mockConnection, atLeastOnce()).close(anyInt());

		verify(mockConnectionFactory, times(1)).newConnection((ExecutorService) null);

	}

	@Test
	public void testCloseInvalidConnection() throws Exception {

		com.rabbitmq.client.ConnectionFactory mockConnectionFactory = mock(com.rabbitmq.client.ConnectionFactory.class);
		com.rabbitmq.client.Connection mockConnection1 = mock(com.rabbitmq.client.Connection.class);
		com.rabbitmq.client.Connection mockConnection2 = mock(com.rabbitmq.client.Connection.class);

		when(mockConnectionFactory.newConnection((ExecutorService) null)).thenReturn(mockConnection1).thenReturn(mockConnection2);
		// simulate a dead connection
		when(mockConnection1.isOpen()).thenReturn(false);

		AbstractConnectionFactory connectionFactory = createConnectionFactory(mockConnectionFactory);

		Connection connection = connectionFactory.createConnection();
		// the dead connection should be discarded
		connection.createChannel(false);
		verify(mockConnectionFactory, times(2)).newConnection((ExecutorService) null);
		verify(mockConnection2, times(1)).createChannel();

		connectionFactory.destroy();
		verify(mockConnection2, times(1)).close(anyInt());

	}

	@Test
	public void testDestroyBeforeUsed() throws Exception {

		com.rabbitmq.client.ConnectionFactory mockConnectionFactory = mock(com.rabbitmq.client.ConnectionFactory.class);

		AbstractConnectionFactory connectionFactory = createConnectionFactory(mockConnectionFactory);
		connectionFactory.destroy();

		verify(mockConnectionFactory, never()).newConnection((ExecutorService) null);
	}

}
