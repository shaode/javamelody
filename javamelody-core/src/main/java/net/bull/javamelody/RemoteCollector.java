/*
 * Copyright 2008-2012 by Emeric Vernat
 *
 *     This file is part of Java Melody.
 *
 * Java Melody is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Java Melody is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Java Melody.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.bull.javamelody;

import static net.bull.javamelody.HttpParameters.CONNECTIONS_PART;
import static net.bull.javamelody.HttpParameters.DATABASE_PART;
import static net.bull.javamelody.HttpParameters.EXPLAIN_PLAN_PART;
import static net.bull.javamelody.HttpParameters.GRAPH_PARAMETER;
import static net.bull.javamelody.HttpParameters.HEAP_HISTO_PART;
import static net.bull.javamelody.HttpParameters.HEIGHT_PARAMETER;
import static net.bull.javamelody.HttpParameters.JROBINS_PART;
import static net.bull.javamelody.HttpParameters.OTHER_JROBINS_PART;
import static net.bull.javamelody.HttpParameters.PART_PARAMETER;
import static net.bull.javamelody.HttpParameters.PROCESSES_PART;
import static net.bull.javamelody.HttpParameters.REQUEST_PARAMETER;
import static net.bull.javamelody.HttpParameters.SESSIONS_PART;
import static net.bull.javamelody.HttpParameters.SESSION_ID_PARAMETER;
import static net.bull.javamelody.HttpParameters.WIDTH_PARAMETER;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collecteur de données pour une application sur un ou plusieurs serveur(s) distant() : utilisé par serveur de collecte et par IHM Swing.
 * @author Emeric Vernat
 */
class RemoteCollector {
	private final String application;
	private List<URL> urls;
	private Collector collector;
	private List<JavaInformations> javaInformationsList;
	private boolean aggregationDisabled;

	/**
	 * Constructeur.
	 * @param application Nom de l'application
	 * @param urls URLs
	 */
	RemoteCollector(String application, List<URL> urls) {
		super();
		assert application != null;
		assert urls != null;
		this.application = application;
		this.urls = urls;
	}

	String collectData() throws IOException {
		return collectDataWithUrls(urls);
	}

	private String collectDataWithUrls(List<URL> urlsForCollect) throws IOException {
		final List<JavaInformations> list = new ArrayList<JavaInformations>();
		final StringBuilder sb = new StringBuilder();
		for (final URL url : urlsForCollect) {
			final List<Serializable> serialized = new LabradorRetriever(url).call();
			final List<Counter> counters = new ArrayList<Counter>();
			for (final Serializable serializable : serialized) {
				if (serializable instanceof Counter) {
					final Counter counter = (Counter) serializable;
					counter.setApplication(application);
					counters.add(counter);
				} else if (serializable instanceof JavaInformations) {
					final JavaInformations newJavaInformations = (JavaInformations) serializable;
					list.add(newJavaInformations);
				} else if (serializable instanceof String) {
					sb.append(serializable).append('\n');
				}
			}
			if (this.collector == null || aggregationDisabled) {
				this.collector = new Collector(application, counters);
			} else {
				addRequestsAndErrors(counters);
			}
		}
		this.javaInformationsList = list;
		final String messageForReport;
		if (sb.length() == 0) {
			messageForReport = null;
		} else {
			messageForReport = sb.toString();
		}
		return messageForReport;
	}

	String executeActionAndCollectData(Action action, String counterName, String sessionId,
			String threadId, String jobId) throws IOException {
		assert action != null;
		final List<URL> actionUrls = new ArrayList<URL>(urls.size());
		for (final URL url : urls) {
			final StringBuilder actionUrl = new StringBuilder(url.toString());
			actionUrl.append("&action=").append(action);
			if (counterName != null) {
				actionUrl.append("&counter=").append(counterName);
			}
			if (sessionId != null) {
				actionUrl.append("&sessionId=").append(sessionId);
			}
			if (threadId != null) {
				actionUrl.append("&threadId=").append(threadId);
			}
			if (jobId != null) {
				actionUrl.append("&jobId=").append(jobId);
			}
			actionUrls.add(new URL(actionUrl.toString()));
		}
		return collectDataWithUrls(actionUrls);
	}

	List<SessionInformations> collectSessionInformations(String sessionId) throws IOException {
		// sessionId est null si on veut toutes les sessions
		if (sessionId == null) {
			// récupération à la demande des sessions
			final List<SessionInformations> sessionsInformations = new ArrayList<SessionInformations>();
			for (final URL url : urls) {
				final URL sessionsUrl = new URL(url.toString() + '&' + PART_PARAMETER + '='
						+ SESSIONS_PART);
				final LabradorRetriever labradorRetriever = new LabradorRetriever(sessionsUrl);
				final List<SessionInformations> sessions = labradorRetriever.call();
				sessionsInformations.addAll(sessions);
			}
			SessionListener.sortSessions(sessionsInformations);
			return sessionsInformations;
		}
		SessionInformations found = null;
		for (final URL url : urls) {
			final URL sessionsUrl = new URL(url.toString() + '&' + PART_PARAMETER + '='
					+ SESSIONS_PART + '&' + SESSION_ID_PARAMETER + '=' + sessionId);
			final LabradorRetriever labradorRetriever = new LabradorRetriever(sessionsUrl);
			final SessionInformations session = (SessionInformations) labradorRetriever.call();
			if (session != null) {
				found = session;
				break;
			}
		}
		if (found == null) {
			// si found est toujours null, alors la session a été invalidée
			return Collections.emptyList();
		}
		return Collections.singletonList(found);
	}

	HeapHistogram collectHeapHistogram() throws IOException {
		// récupération à la demande des HeapHistogram
		HeapHistogram heapHistoTotal = null;
		for (final URL url : urls) {
			final URL heapHistoUrl = new URL(url.toString() + '&' + PART_PARAMETER + '='
					+ HEAP_HISTO_PART);
			final LabradorRetriever labradorRetriever = new LabradorRetriever(heapHistoUrl);
			final HeapHistogram heapHisto = labradorRetriever.call();
			if (heapHistoTotal == null) {
				heapHistoTotal = heapHisto;
			} else {
				heapHistoTotal.add(heapHisto);
			}
		}
		return heapHistoTotal;
	}

	DatabaseInformations collectDatabaseInformations(int requestIndex) throws IOException {
		final URL url = urls.get(0);
		final URL databaseUrl = new URL(url.toString() + '&' + PART_PARAMETER + '=' + DATABASE_PART
				+ '&' + REQUEST_PARAMETER + '=' + requestIndex);
		return new LabradorRetriever(databaseUrl).call();
	}

	List<List<ConnectionInformations>> collectConnectionInformations() throws IOException {
		// récupération à la demande des connections
		final List<List<ConnectionInformations>> connectionInformations = new ArrayList<List<ConnectionInformations>>();
		for (final URL url : urls) {
			final URL connectionsUrl = new URL(url.toString() + '&' + PART_PARAMETER + '='
					+ CONNECTIONS_PART);
			final LabradorRetriever labradorRetriever = new LabradorRetriever(connectionsUrl);
			final List<ConnectionInformations> connections = labradorRetriever.call();
			connectionInformations.add(connections);
		}
		return connectionInformations;
	}

	Map<String, List<ProcessInformations>> collectProcessInformations() throws IOException {
		// récupération à la demande des processus
		final String title = I18N.getString("Processus");
		final Map<String, List<ProcessInformations>> processesByTitle = new LinkedHashMap<String, List<ProcessInformations>>();
		for (final URL url : urls) {
			final URL processUrl = new URL(url.toString() + '&' + PART_PARAMETER + '='
					+ PROCESSES_PART);
			final LabradorRetriever labradorRetriever = new LabradorRetriever(processUrl);
			final List<ProcessInformations> processList = labradorRetriever.call();
			processesByTitle.put(title + " (" + getHostAndPort(url) + ')', processList);
		}
		return processesByTitle;
	}

	List<List<ThreadInformations>> getThreadInformationsLists() {
		final List<List<ThreadInformations>> result = new ArrayList<List<ThreadInformations>>();
		for (final JavaInformations javaInformations : this.javaInformationsList) {
			result.add(new ArrayList<ThreadInformations>(javaInformations
					.getThreadInformationsList()));
		}
		return result;
	}

	Map<String, byte[]> collectJRobins(int width, int height) throws IOException {
		final URL url = urls.get(0);
		final URL jrobinNamesUrl = new URL(url.toString() + '&' + PART_PARAMETER + '='
				+ JROBINS_PART + '&' + WIDTH_PARAMETER + '=' + width + '&' + HEIGHT_PARAMETER + '='
				+ height);
		final LabradorRetriever labradorRetriever = new LabradorRetriever(jrobinNamesUrl);
		return labradorRetriever.call();
	}

	Map<String, byte[]> collectOtherJRobins(int width, int height) throws IOException {
		final URL url = urls.get(0);
		final URL otherJRobinNamesUrl = new URL(url.toString() + '&' + PART_PARAMETER + '='
				+ OTHER_JROBINS_PART + '&' + WIDTH_PARAMETER + '=' + width + '&' + HEIGHT_PARAMETER
				+ '=' + height);
		final LabradorRetriever labradorRetriever = new LabradorRetriever(otherJRobinNamesUrl);
		return labradorRetriever.call();
	}

	byte[] collectJRobin(String graphName, int width, int height) throws IOException {
		final URL url = urls.get(0);
		final URL jrobinUrl = new URL(url.toString() + '&' + GRAPH_PARAMETER + '=' + graphName
				+ '&' + PART_PARAMETER + '=' + JROBINS_PART + '&' + WIDTH_PARAMETER + '=' + width
				+ '&' + HEIGHT_PARAMETER + '=' + height);
		final LabradorRetriever labradorRetriever = new LabradorRetriever(jrobinUrl);
		return labradorRetriever.call();
	}

	String collectSqlRequestExplainPlan(String sqlRequest) throws IOException {
		final URL url = urls.get(0);
		final URL explainPlanUrl = new URL(url.toString() + '&' + PART_PARAMETER + '='
				+ EXPLAIN_PLAN_PART);
		final Map<String, String> headers = Collections.singletonMap(REQUEST_PARAMETER, sqlRequest);
		final LabradorRetriever labradorRetriever = new LabradorRetriever(explainPlanUrl, headers);
		return labradorRetriever.call();
	}

	private void addRequestsAndErrors(List<Counter> counters) {
		for (final Counter newCounter : counters) {
			final Counter counter = collector.getCounterByName(newCounter.getName());
			// counter.isDisplayed() peut changer pour spring, ejb ou services selon l'utilisation
			counter.setDisplayed(newCounter.isDisplayed());
			counter.addRequestsAndErrors(newCounter);
		}
	}

	static String getHostAndPort(URL url) {
		if (url.getPort() != -1) {
			return url.getHost() + ':' + url.getPort();
		}
		// port est -1 si c'est le port par défaut (80)
		return url.getHost();
	}

	String getApplication() {
		return application;
	}

	List<URL> getURLs() {
		return urls;
	}

	void setURLs(List<URL> newURLs) {
		assert urls != null;
		this.urls = newURLs;
	}

	Collector getCollector() {
		return collector;
	}

	List<JavaInformations> getJavaInformationsList() {
		return javaInformationsList;
	}

	// cette méthode est utilisée dans l'ihm Swing
	void disableAggregation() {
		aggregationDisabled = true;
	}
}
