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

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Timer;

import javax.naming.NoInitialContextException;
import javax.servlet.ServletContext;

import org.junit.Before;
import org.junit.Test;

/**
 * Test unitaire de la classe MailReport.
 * @author Emeric Vernat
 */
public class TestMailReport {
	/** Check. */
	@Before
	public void setUp() {
		Utils.initialize();
	}

	/** Test. */
	@Test
	public void testScheduleReportMail() {
		final Timer timer = new Timer("test timer", true);
		try {
			final Counter counter = new Counter("http", null);
			final Collector collector = new Collector("test", Collections.singletonList(counter));
			MailReport.scheduleReportMailForLocalServer(collector, timer);
		} finally {
			timer.cancel();
		}
		// n'importe
		assertNotNull("MailReport", timer.purge());
	}

	/** Test.
	 * @throws Exception e */
	@Test
	public void testSendReportMail() throws Exception { // NOPMD
		final Counter counter = new Counter("http", null);
		final Collector collector = new Collector("test", Collections.singletonList(counter));
		final List<JavaInformations> javaInformationslist = Collections
				.singletonList(new JavaInformations(null, true));
		setProperty(Parameter.ADMIN_EMAILS, "evernat@free.fr");
		setProperty(Parameter.MAIL_SESSION, "mail/Session");
		try {
			new MailReport().sendReportMail(collector, false, javaInformationslist, Period.SEMAINE);
		} catch (final NoInitialContextException e) {
			assertNotNull("ok", e);
		}

		// sendReportMailForLocalServer
		final String path = "path";
		final ServletContext context = createNiceMock(ServletContext.class);
		expect(context.getMajorVersion()).andReturn(3).anyTimes();
		expect(context.getMinorVersion()).andReturn(0).anyTimes();
		expect(context.getContextPath()).andReturn(path).anyTimes();
		replay(context);
		Parameters.initialize(context);
		try {
			new MailReport().sendReportMailForLocalServer(collector, Period.SEMAINE);
		} catch (final NoInitialContextException e) {
			assertNotNull("ok", e);
		}
		verify(context);
	}

	/** Test. */
	@Test
	public void testGetNextExecutionDate() {
		assertNotNull("getNextExecutionDate", MailReport.getNextExecutionDate(Period.JOUR));
		assertNotNull("getNextExecutionDate", MailReport.getNextExecutionDate(Period.SEMAINE));
		assertNotNull("getNextExecutionDate", MailReport.getNextExecutionDate(Period.MOIS));
	}

	/** Test. */
	@Test
	public void testGetMailPeriod() {
		for (final Period period : Period.values()) {
			assertNotNull("getMailCode", period.getMailCode());
			assertEquals("valueOfByMailCode", period,
					Period.valueOfByMailCode(period.getMailCode()));
		}
		try {
			Period.valueOfByMailCode("unknown");
		} catch (final IllegalArgumentException e) {
			assertNotNull("ok", e);
		}

		assertEquals("getMailPeriods", Collections.singletonList(Period.SEMAINE),
				MailReport.getMailPeriods());
		setProperty(Parameter.MAIL_PERIODS, Period.JOUR.getMailCode());
		assertEquals("getMailPeriods", Collections.singletonList(Period.JOUR),
				MailReport.getMailPeriods());
	}

	private static void setProperty(Parameter parameter, String value) {
		Utils.setProperty(parameter, value);
	}
}
