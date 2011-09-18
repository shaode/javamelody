/*
 * Copyright 2008-2010 by Emeric Vernat
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.io.IOException;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import net.bull.javamelody.swing.util.MSwingUtilities;

/**
 * Panel principal.
 * @author Emeric Vernat
 */
class MainPanel extends MelodyPanel {
	private static final Color BACKGROUND = new Color(230, 230, 230);
	private static final long serialVersionUID = 1L;

	private final TabbedPane tabbedPane = new TabbedPane();
	private final URL monitoringUrl;
	private final JScrollPane scrollPane;

	// TODO mettre exporter en pdf, rtf, xml et json dans un menu contextuel

	MainPanel(RemoteCollector remoteCollector, URL monitoringUrl) throws IOException {
		super(remoteCollector, new BorderLayout());
		assert monitoringUrl != null;
		this.monitoringUrl = monitoringUrl;

		scrollPane = new JScrollPane();
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		scrollPane.getVerticalScrollBar().setUnitIncrement(20);

		final JPanel mainTabPanel = new JPanel(new BorderLayout());
		mainTabPanel.setOpaque(false);
		mainTabPanel.add(new MainButtonsPanel(remoteCollector, monitoringUrl), BorderLayout.NORTH);
		mainTabPanel.add(scrollPane, BorderLayout.CENTER);

		// TODO translation
		tabbedPane.addTab("Main", mainTabPanel);
		tabbedPane.setTabComponentAt(0, null);
		add(tabbedPane, BorderLayout.CENTER);

		refreshMainTab();
	}

	private void refreshMainTab() throws IOException {
		final int position = scrollPane.getVerticalScrollBar().getValue();
		final ScrollingPanel scrollingPanel = new ScrollingPanel(getRemoteCollector(),
				monitoringUrl);
		scrollPane.setViewportView(scrollingPanel);
		scrollPane.getVerticalScrollBar().setValue(position);
	}

	static void refreshMainTabFromChild(Component child) throws IOException {
		final MainPanel mainPanel = MSwingUtilities.getAncestorOfClass(MainPanel.class, child);
		mainPanel.refreshMainTab();
	}

	static void addOngletFromChild(Component child, JPanel panel) {
		panel.setOpaque(true);
		panel.setBackground(BACKGROUND);
		panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
		final MainPanel mainPanel = MSwingUtilities.getAncestorOfClass(MainPanel.class, child);
		mainPanel.addOnglet(panel);
	}

	private void addOnglet(JPanel panel) {
		tabbedPane.addTab(panel.getName(), panel);
		tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);
	}
}
