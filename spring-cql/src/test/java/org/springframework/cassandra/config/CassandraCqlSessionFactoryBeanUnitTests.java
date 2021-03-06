/*
 *  Copyright 2013-2016 the original author or authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cassandra.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cassandra.core.CqlOperations;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

/**
 * The CassandraCqlSessionFactoryBeanUnitTests class is a test suite of test cases testing the contract and
 * functionality of the {@link CassandraCqlSessionFactoryBean} class.
 *
 * @author John Blum
 * @see org.springframework.cassandra.config.CassandraCqlSessionFactoryBean
 * @see <a href="https://jira.spring.io/browse/DATACASS-219>DATACASS-219</a>
 * @since 1.5.0
 */
@RunWith(MockitoJUnitRunner.class)
public class CassandraCqlSessionFactoryBeanUnitTests {

	@Rule public ExpectedException exception = ExpectedException.none();

	@Mock private Cluster mockCluster;

	@Mock private Session mockSession;

	private CassandraCqlSessionFactoryBean factoryBean;

	protected <T> List<T> asList(T... array) {
		return new ArrayList<T>(Arrays.asList(array));
	}

	protected void assertNonNullEmptyCollection(Collection<?> collection) {
		assertThat(collection).isNotNull();
		assertThat(collection.isEmpty()).isTrue();
	}

	@Before
	public void setup() {
		factoryBean = spy(new CassandraCqlSessionFactoryBean());
	}

	@Test
	public void cassandraCqlSessionFactoryBeanIsSingleton() {
		assertThat(factoryBean.isSingleton()).isTrue();
	}

	@Test
	public void objectTypeWhenSessionHasNotBeenInitializedIsSessionClass() {
		assertThat(factoryBean.getObject()).isNull();
		assertThat(factoryBean.<Session> getObjectType()).isEqualTo(Session.class);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterPropertiesSetInitializesSessionWithKeyspaceAndExecutesStartupScripts() throws Exception {
		List<String> expectedStartupScripts = asList("/path/to/schema.cql", "/path/to/data.cql");

		CqlOperations mockCqlOperations = mock(CqlOperations.class);

		doReturn(mockSession).when(factoryBean).connect(eq("TestKeyspace"));
		doReturn(mockCqlOperations).when(factoryBean).newCqlOperations(eq(mockSession));

		factoryBean.setKeyspaceName("TestKeyspace");
		factoryBean.setStartupScripts(expectedStartupScripts);

		assertThat(factoryBean.getKeyspaceName()).isEqualTo("TestKeyspace");
		assertThat(factoryBean.getStartupScripts()).isEqualTo(expectedStartupScripts);

		factoryBean.afterPropertiesSet();

		assertThat(factoryBean.getObjectType()).isEqualTo(mockSession.getClass());
		assertThat(factoryBean.getObject()).isEqualTo(mockSession);
		assertThat(factoryBean.getSession()).isEqualTo(mockSession);

		InOrder inOrder = inOrder(factoryBean);

		inOrder.verify(factoryBean, times(1)).connect(eq("TestKeyspace"));
		inOrder.verify(factoryBean, times(1)).executeScripts(eq(expectedStartupScripts));
		inOrder.verify(factoryBean, times(1)).newCqlOperations(eq(mockSession));
		verify(mockCqlOperations, times(1)).execute(eq(expectedStartupScripts.get(0)));
		verify(mockCqlOperations, times(1)).execute(eq(expectedStartupScripts.get(1)));
	}

	@Test
	public void connectToSystemKeyspace() {
		when(mockCluster.connect()).thenReturn(mockSession);

		factoryBean.setCluster(mockCluster);

		assertThat(factoryBean.connect(null)).isEqualTo(mockSession);

		verify(mockCluster, times(1)).connect();
		verify(mockCluster, never()).connect(anyString());
	}

	@Test
	public void connectToTargetKeyspace() {
		when(mockCluster.connect(eq("TestKeyspace"))).thenReturn(mockSession);

		factoryBean.setCluster(mockCluster);

		assertThat(factoryBean.connect("TestKeyspace")).isEqualTo(mockSession);

		verify(mockCluster, never()).connect();
		verify(mockCluster, times(1)).connect(eq("TestKeyspace"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void destroySessionAndExecutesShutdownScripts() throws Exception {
		List<String> expectedShutdownScripts = asList("/path/to/shutdown.cql");

		CqlOperations mockCqlOperations = mock(CqlOperations.class);

		doReturn(mockSession).when(factoryBean).getSession();
		doReturn(mockCqlOperations).when(factoryBean).newCqlOperations(eq(mockSession));

		factoryBean.setShutdownScripts(expectedShutdownScripts);
		factoryBean.destroy();

		InOrder inOrder = inOrder(factoryBean, mockSession);

		inOrder.verify(factoryBean, times(1)).executeScripts(eq(expectedShutdownScripts));
		verify(mockCqlOperations, times(1)).execute(eq(expectedShutdownScripts.get(0)));
		inOrder.verify(mockSession, times(1)).close();
	}

	@Test
	public void isConnectedWithNullSessionIsFalse() {
		assertThat(factoryBean.getObject()).isNull();
		assertThat(factoryBean.isConnected()).isFalse();
	}

	@Test
	public void isConnectedWithClosedSessionIsFalse() {
		doReturn(mockSession).when(factoryBean).getObject();
		when(mockSession.isClosed()).thenReturn(true);
		assertThat(factoryBean.isConnected()).isFalse();
		verify(mockSession, times(1)).isClosed();
	}

	@Test
	public void isConnectedWithOpenSessionIsTrue() {
		doReturn(mockSession).when(factoryBean).getObject();
		when(mockSession.isClosed()).thenReturn(false);
		assertThat(factoryBean.isConnected()).isTrue();
		verify(mockSession, times(1)).isClosed();
	}

	@Test
	public void setAndGetCluster() {
		factoryBean.setCluster(mockCluster);
		assertThat(factoryBean.getCluster()).isEqualTo(mockCluster);
	}

	@Test
	public void setClusterToNullThrowsIllegalArgumentException() {

		try {
			factoryBean.setCluster(null);
			fail("Missing IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			assertThat(e).hasMessageContaining("Cluster must not be null");
		}

	}

	@Test
	public void getClusterWhenUninitializedThrowsIllegalStateException() {

		try {
			factoryBean.getCluster();
			fail("Missing IllegalStateException");
		} catch (IllegalStateException e) {
			assertThat(e).hasMessageContaining("Cluster was not properly initialized");
		}
	}

	@Test
	public void setAndGetKeyspaceName() {
		assertThat(factoryBean.getKeyspaceName()).isNull();

		factoryBean.setKeyspaceName("TEST");

		assertThat(factoryBean.getKeyspaceName()).isEqualTo("TEST");

		factoryBean.setKeyspaceName(null);

		assertThat(factoryBean.getKeyspaceName()).isNull();
	}

	@Test
	public void getSessionWhenUninitializedThrowsIllegalStateException() {

		assertThat(factoryBean.getObject()).isNull();

		try {
			factoryBean.getSession();
			fail("Missing IllegalStateException");
		} catch (IllegalStateException e) {
			assertThat(e).hasMessageContaining("Session was not properly initialized");
		}

	}

	@Test
	public void setAndGetStartupScripts() {
		assertNonNullEmptyCollection(factoryBean.getStartupScripts());

		List<String> expectedStartupScripts = asList("/path/to/schema.cql", "/path/to/data.cql");

		factoryBean.setStartupScripts(expectedStartupScripts);

		List<String> actualStartupScripts = factoryBean.getStartupScripts();

		assertThat(actualStartupScripts).isNotSameAs(expectedStartupScripts).isEqualTo(expectedStartupScripts);

		factoryBean.setStartupScripts(null);

		assertNonNullEmptyCollection(factoryBean.getStartupScripts());
	}

	@Test
	public void startupScriptsAreImmutable() {
		List<String> startupScripts = asList("/path/to/startup.cql");

		factoryBean.setStartupScripts(startupScripts);

		List<String> actualStartupScripts = factoryBean.getStartupScripts();

		assertThat(actualStartupScripts).isEqualTo(startupScripts).isNotSameAs(startupScripts);

		startupScripts.add("/path/to/another.cql");

		actualStartupScripts = factoryBean.getStartupScripts();

		assertThat(actualStartupScripts).isNotEqualTo(startupScripts);
		assertThat(actualStartupScripts).hasSize(1);
		assertThat(actualStartupScripts.get(0)).isEqualTo(startupScripts.get(0));

		try {
			exception.expect(UnsupportedOperationException.class);
			actualStartupScripts.add("/path/to/yetAnother.cql");
		} finally {
			assertThat(actualStartupScripts).hasSize(1);
		}
	}

	@Test
	public void setAndGetShutdownScripts() {
		assertNonNullEmptyCollection(factoryBean.getShutdownScripts());

		List<String> expectedShutdownScripts = asList("/path/to/backup.cql", "/path/to/dropTables.cql");

		factoryBean.setShutdownScripts(expectedShutdownScripts);

		List<String> actualShutdownScripts = factoryBean.getShutdownScripts();

		assertThat(actualShutdownScripts).isEqualTo(expectedShutdownScripts).isNotSameAs(expectedShutdownScripts);

		factoryBean.setShutdownScripts(null);

		assertNonNullEmptyCollection(factoryBean.getShutdownScripts());
	}

	@Test
	public void shutdownScriptsAreImmutable() {
		List<String> shutdownScripts = asList("/path/to/shutdown.cql");

		factoryBean.setShutdownScripts(shutdownScripts);

		List<String> actualShutdownScripts = factoryBean.getShutdownScripts();

		assertThat(actualShutdownScripts).isEqualTo(shutdownScripts).isNotSameAs(shutdownScripts);

		shutdownScripts.add("/path/to/corruptSession.cql");

		actualShutdownScripts = factoryBean.getShutdownScripts();

		assertThat(actualShutdownScripts).isNotEqualTo(shutdownScripts);
		assertThat(actualShutdownScripts).hasSize(1);

		try {
			exception.expect(UnsupportedOperationException.class);
			actualShutdownScripts.add("/path/to/blowUpCluster.cql");
		} finally {
			assertThat(actualShutdownScripts).hasSize(1);
		}
	}
}
