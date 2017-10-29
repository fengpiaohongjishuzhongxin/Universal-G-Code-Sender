/*
    Copyright 2017 Will Winder

    This file is part of Universal Gcode Sender (UGS).

    UGS is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    UGS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with UGS.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.willwinder.ugs.platform.probe;

import com.google.gson.Gson;
import com.willwinder.ugs.nbm.visualizer.shared.Renderable;
import com.willwinder.ugs.nbm.visualizer.shared.RenderableUtils;
import com.willwinder.ugs.nbp.lib.lookup.CentralLookup;
import com.willwinder.ugs.nbp.lib.services.LocalizingService;
import com.willwinder.ugs.platform.probe.ProbeService.ProbeContext;
import com.willwinder.ugs.platform.probe.renderable.CornerProbePathPreview;
import com.willwinder.ugs.platform.probe.renderable.ZProbePathPreview;
import com.willwinder.universalgcodesender.i18n.Localization;
import com.willwinder.universalgcodesender.listeners.UGSEventListener;
import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.model.UGSEvent;
import com.willwinder.universalgcodesender.model.UnitUtils.Units;
import com.willwinder.universalgcodesender.model.WorkCoordinateSystem;
import static com.willwinder.universalgcodesender.model.WorkCoordinateSystem.*;
import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import net.miginfocom.swing.MigLayout;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.windows.TopComponent;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//com.willwinder.ugs.platform.probe//CornerProbeTopComponent//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "CornerProbeTopComponentTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE", 
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "output", openAtStartup = false)
@ActionID(
        category = "Window",
        id = LocalizingService.ProbeActionId)
@ActionReference(path = LocalizingService.PLUGIN_WINDOW)
@TopComponent.OpenActionRegistration(
        displayName = "Probe",
        preferredID = "ProbeModule"
)
public final class ProbeTopComponent extends TopComponent implements UGSEventListener {
    private Renderable active = null;
    private CornerProbePathPreview cornerRenderable = new CornerProbePathPreview(
            Localization.getString("probe.visualizer.corner-preview"));
    private ZProbePathPreview zRenderable = new ZProbePathPreview(
            Localization.getString("probe.visualizer.z-preview"));

    private static final String X_OFFSET = Localization.getString("autoleveler.option.offset-x") + ":";
    private static final String Y_OFFSET = Localization.getString("autoleveler.option.offset-y") + ":";
    //private static final String Z_OFFSET = Localization.getString("autoleveler.option.offset-z") + ":";
    private static final String Z_OFFSET = Localization.getString("probe.plate-thickness");
    private static final String X_DISTANCE = Localization.getString("probe.x-distance") + ":";
    private static final String Y_DISTANCE = Localization.getString("probe.y-distance") + ":";
    private static final String Z_DISTANCE = Localization.getString("probe.probe-distance") + ":";

    protected class ProbeSettings {
        double xyzXDistance;
        double xyzYDistance;
        double xyzZDistance;
        double xyzXOffset;
        double xyzYOffset;
        double xyzZOffset;

        double outsideXDistance;
        double outsideYDistance;
        double outsideXOffset;
        double outsideYOffset;

        double zDistance;
        double zOffset;

        double insideXDistance;
        double insideYDistance;
        double insideXOffset;
        double insideYOffset;

        int settingsWorkCoordinateIdx;
        int settingsUnitsIdx;
        double settingsProbeDiameter;
        double settingsFastFindRate;
        double settingsSlowMeasureRate;
        double settingsRetractAmount;

        int selectedTabIdx;
    }

    // xyz tab
    private static final String XYZ_TAB = "XYZ";
    private SpinnerNumberModel xyzXDistanceModel;
    private SpinnerNumberModel xyzYDistanceModel;
    private SpinnerNumberModel xyzZDistanceModel;
    private SpinnerNumberModel xyzXOffsetModel;
    private SpinnerNumberModel xyzYOffsetModel;
    private SpinnerNumberModel xyzZOffsetModel;
    private final JButton measureXYZ = new JButton(Localization.getString("probe.measure.outside-corner"));

    // outside tab
    private static final String OUTSIDE_TAB = "XY";
    private SpinnerNumberModel outsideXDistanceModel;
    private SpinnerNumberModel outsideYDistanceModel;
    private SpinnerNumberModel outsideXOffsetModel;
    private SpinnerNumberModel outsideYOffsetModel;
    private final JButton measureOutside = new JButton(Localization.getString("probe.measure.outside-corner"));

    // z-probe tab
    private static final String Z_TAB = "Z";
    private final SpinnerNumberModel zProbeDistance;
    private final SpinnerNumberModel zProbeOffset;
    private final JButton  zProbeButton = new JButton(Localization.getString("probe.button"));


    // inside tab
    private SpinnerNumberModel insideXDistanceModel;
    private SpinnerNumberModel insideYDistanceModel;
    private SpinnerNumberModel insideXOffsetModel;
    private SpinnerNumberModel insideYOffsetModel;
    private final JButton measureInside = new JButton(Localization.getString("probe.measure.inside-corner"));

    // settings
    private JComboBox<WorkCoordinateSystem> settingsWorkCoordinate;
    private JComboBox<String> settingsUnits;
    private SpinnerNumberModel settingsProbeDiameter;
    private SpinnerNumberModel settingsFastFindRate;
    private SpinnerNumberModel settingsSlowMeasureRate;
    private SpinnerNumberModel settingsRetractAmount;


    private static final String SETTINGS_TAB = "Settings";

    private final JTabbedPane jtp = new JTabbedPane(JTabbedPane.LEFT);

    private final ProbeService ps2;
    private final BackendAPI backend;

    public ProbeTopComponent() {
        setName(LocalizingService.ProbeTitle);
        setToolTipText(LocalizingService.ProbeTooltip);

        backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        backend.addUGSEventListener(this);

        ps2 = new ProbeService(backend);

        double largeSpinner = 1000000;

        // XYZ TAB
        xyzXDistanceModel = new SpinnerNumberModel(10., -largeSpinner, largeSpinner, 0.1);
        xyzYDistanceModel = new SpinnerNumberModel(10., -largeSpinner, largeSpinner, 0.1);
        xyzZDistanceModel = new SpinnerNumberModel(10., -largeSpinner, largeSpinner, 0.1);
        xyzXOffsetModel = new SpinnerNumberModel(2., -largeSpinner, largeSpinner, 0.1);
        xyzYOffsetModel = new SpinnerNumberModel(2., -largeSpinner, largeSpinner, 0.1);
        xyzZOffsetModel = new SpinnerNumberModel(2., -largeSpinner, largeSpinner, 0.1);

        // OUTSIDE TAB
        outsideXDistanceModel = new SpinnerNumberModel(10., -largeSpinner, largeSpinner, 0.1);
        outsideYDistanceModel = new SpinnerNumberModel(10., -largeSpinner, largeSpinner, 0.1);
        outsideXOffsetModel = new SpinnerNumberModel(2., -largeSpinner, largeSpinner, 0.1);
        outsideYOffsetModel = new SpinnerNumberModel(2., -largeSpinner, largeSpinner, 0.1);

        // Z PROBE TAB
        zProbeDistance = new SpinnerNumberModel(10., -largeSpinner, largeSpinner, 0.1);
        zProbeOffset = new SpinnerNumberModel(10., -largeSpinner, largeSpinner, 0.1);

        // INSIDE TAB
        insideXDistanceModel = new SpinnerNumberModel(10., -largeSpinner, largeSpinner, 0.1);
        insideYDistanceModel = new SpinnerNumberModel(10., -largeSpinner, largeSpinner, 0.1);
        insideXOffsetModel = new SpinnerNumberModel(2., -largeSpinner, largeSpinner, 0.1);
        insideYOffsetModel = new SpinnerNumberModel(2., -largeSpinner, largeSpinner, 0.1);

        // SETTINGS TAB
        settingsWorkCoordinate = new JComboBox<>(new WorkCoordinateSystem[]{G54, G55, G56, G57, G58, G59});
        settingsUnits = new JComboBox<>(new String[]{
            Localization.getString("mainWindow.swing.mmRadioButton"),
            Localization.getString("mainWindow.swing.inchRadioButton")
        });
        settingsProbeDiameter = new SpinnerNumberModel(10., 0., largeSpinner, 0.1);
        settingsFastFindRate = new SpinnerNumberModel(250., 1, largeSpinner, 1.);
        settingsSlowMeasureRate = new SpinnerNumberModel(100., 1, largeSpinner, 1.);
        settingsRetractAmount = new SpinnerNumberModel(15., 10, largeSpinner, 1.);

        measureXYZ.addActionListener(e -> {
                ProbeContext pc = new ProbeContext(
                        get(settingsProbeDiameter), backend.getMachinePosition(),
                        get(xyzXDistanceModel), get(xyzYDistanceModel), get(xyzZDistanceModel),
                        get(xyzXOffsetModel), get(xyzYOffsetModel), get(xyzZOffsetModel),
                        get(settingsFastFindRate), get(settingsSlowMeasureRate),
                        get(settingsRetractAmount), getUnits(), get(settingsWorkCoordinate));
                this.cornerRenderable.setContext(pc, backend.getWorkPosition(), backend.getMachinePosition());
                ps2.performXYZProbe(pc);
            });

        measureOutside.addActionListener(e -> {
                ProbeContext pc = new ProbeContext(
                        get(settingsProbeDiameter), backend.getMachinePosition(),
                        get(outsideXDistanceModel), get(outsideYDistanceModel), 0.,
                        get(outsideXOffsetModel), get(outsideYOffsetModel), 0.,
                        get(settingsFastFindRate), get(settingsSlowMeasureRate),
                        get(settingsRetractAmount), getUnits(), get(settingsWorkCoordinate));
                this.cornerRenderable.setContext(pc, backend.getWorkPosition(), backend.getMachinePosition());
                ps2.performOutsideCornerProbe(pc);
            });

        /*
        measureInside.addActionListener((e) -> {
            ProbeContext pc = new ProbeContext(
                1, backend.getMachinePosition(),
                get(insideXDistanceModel), get(insideYDistanceModel), 100., 1);
                ps2.performInsideCornerProbe(pc);
            });
        */

        zProbeButton.addActionListener(e -> {
                ProbeContext pc = new ProbeContext(
                        get(settingsProbeDiameter), backend.getMachinePosition(),
                        0., 0., get(zProbeDistance),
                        0., 0., get(zProbeOffset),
                        get(settingsFastFindRate), get(settingsSlowMeasureRate),
                        get(settingsRetractAmount), getUnits(), get(settingsWorkCoordinate));
                this.zRenderable.setStart(backend.getWorkPosition());
                ps2.performZProbe(pc);
            });

        initComponents();
        updateControls();

        // Listeners...
        this.xyzXDistanceModel.addChangeListener(l -> controlChangeListener());
        this.xyzYDistanceModel.addChangeListener(l -> controlChangeListener());
        this.xyzZDistanceModel.addChangeListener(l -> controlChangeListener());
        this.outsideXDistanceModel.addChangeListener(l -> controlChangeListener());
        this.outsideYDistanceModel.addChangeListener(l -> controlChangeListener());
        this.insideXDistanceModel.addChangeListener(l -> controlChangeListener());
        this.insideYDistanceModel.addChangeListener(l -> controlChangeListener());

        this.zProbeDistance.addChangeListener(l -> controlChangeListener());
        this.zProbeOffset.addChangeListener(l -> controlChangeListener());

        this.xyzXOffsetModel.addChangeListener(l -> controlChangeListener());
        this.xyzYOffsetModel.addChangeListener(l -> controlChangeListener());
        this.xyzZOffsetModel.addChangeListener(l -> controlChangeListener());
        this.outsideXOffsetModel.addChangeListener(l -> controlChangeListener());
        this.outsideYOffsetModel.addChangeListener(l -> controlChangeListener());
        this.insideXOffsetModel.addChangeListener(l -> controlChangeListener());
        this.insideYOffsetModel.addChangeListener(l -> controlChangeListener());

        this.settingsWorkCoordinate.addActionListener(l -> controlChangeListener());
        this.settingsUnits.addActionListener(l -> controlChangeListener());
        this.settingsProbeDiameter.addChangeListener(l -> controlChangeListener());
        this.settingsFastFindRate.addChangeListener(l -> controlChangeListener());
        this.settingsSlowMeasureRate.addChangeListener(l -> controlChangeListener());
        this.settingsRetractAmount.addChangeListener(l -> controlChangeListener());

        this.jtp.addChangeListener(l -> controlChangeListener());
    }

    private void controlChangeListener() {
        Renderable before = active;
        //switch (this.jtp.getTabComponentAt(this.jtp.getSelectedIndex()).getName()) {
        switch (this.jtp.getTitleAt(this.jtp.getSelectedIndex())) {
            case XYZ_TAB:
                // TODO: XYZ Renderable
                active = cornerRenderable;
                cornerRenderable.updateSpacing(
                        get(xyzXDistanceModel),
                        get(xyzYDistanceModel),
                        get(xyzZDistanceModel),
                        get(xyzXOffsetModel),
                        get(xyzYOffsetModel),
                        get(xyzZOffsetModel));
                break;
            case OUTSIDE_TAB:
                active = cornerRenderable;
                cornerRenderable.updateSpacing(
                        get(outsideXDistanceModel),
                        get(outsideYDistanceModel),
                        0,
                        get(outsideXOffsetModel),
                        get(outsideYOffsetModel),
                        0);
                break;
            case Z_TAB:
                active = zRenderable;
                zRenderable.updateSpacing(get(zProbeDistance), get(zProbeOffset));
                break;
            case SETTINGS_TAB:
                active = null;
                break;
        }

        if (before != active) {
            RenderableUtils.removeRenderable(before);
            RenderableUtils.registerRenderable(this.active);
        }
    }

    public void updateControls() {
        boolean enabled = backend.isIdle();
        this.measureInside.setEnabled(enabled);
        this.measureOutside.setEnabled(enabled);
        this.zProbeButton.setEnabled(enabled);
    }

    @Override
    public void UGSEvent(UGSEvent evt) {
        if (evt.isStateChangeEvent()) {
            updateControls();
        }
    }

    private Units getUnits() {
        return this.settingsUnits.getSelectedIndex() == 0 ? Units.MM : Units.INCH;
    }

    // deal with casting the spinner model to a double.
    private static double get(SpinnerNumberModel model) {
        return (double) model.getValue();
    }

    // Helper since getSelectedItem doesn't use generics.
    private static WorkCoordinateSystem get(JComboBox<WorkCoordinateSystem> wcsCombo) {
        return wcsCombo.getItemAt(wcsCombo.getSelectedIndex());
    }

    private void initComponents() {
        // XYZ TAB
        JPanel xyz = new JPanel(new MigLayout("flowy, wrap 3"));
        xyz.add(new JLabel(X_DISTANCE));
        xyz.add(new JLabel(Y_DISTANCE));
        xyz.add(new JLabel(Z_DISTANCE));
        xyz.add(new JSpinner(xyzXDistanceModel), "growx");
        xyz.add(new JSpinner(xyzYDistanceModel), "growx");
        xyz.add(new JSpinner(xyzZDistanceModel), "growx");

        xyz.add(new JLabel(X_OFFSET));
        xyz.add(new JLabel(Y_OFFSET));
        xyz.add(new JLabel(Z_OFFSET));
        xyz.add(new JSpinner(xyzXOffsetModel), "growx");
        xyz.add(new JSpinner(xyzYOffsetModel), "growx");
        xyz.add(new JSpinner(xyzZOffsetModel), "growx");

        xyz.add(measureXYZ, "spanx 2, spany 3, growx, growy");

        // TODO: INSIDE Probe
        JPanel inside = new JPanel(new MigLayout("flowy, wrap 2"));
        inside.add(new JLabel(X_DISTANCE));
        inside.add(new JLabel(Y_DISTANCE));
        inside.add(new JSpinner(insideXDistanceModel), "growx");
        inside.add(new JSpinner(insideYDistanceModel), "growx");

        inside.add(new JLabel(X_OFFSET));
        inside.add(new JLabel(Y_OFFSET));
        inside.add(new JSpinner(insideXOffsetModel), "growx");
        inside.add(new JSpinner(insideYOffsetModel), "growx");

        inside.add(measureInside, "spanx 2, spany 2, growx, growy");

        // OUTSIDE TAB
        JPanel outside = new JPanel(new MigLayout("flowy, wrap 2"));
        outside.add(new JLabel(X_DISTANCE));
        outside.add(new JLabel(Y_DISTANCE));
        outside.add(new JSpinner(outsideXDistanceModel), "growx");
        outside.add(new JSpinner(outsideYDistanceModel), "growx");

        outside.add(new JLabel(X_OFFSET));
        outside.add(new JLabel(Y_OFFSET));
        outside.add(new JSpinner(outsideXOffsetModel), "growx");
        outside.add(new JSpinner(outsideYOffsetModel), "growx");

        outside.add(measureOutside, "spanx 2, spany 2, growx, growy");

        // Z PROBE TAB
        JPanel z = new JPanel(new MigLayout("wrap 4"));
        z.add(new JLabel(Z_OFFSET));
        z.add(new JSpinner(this.zProbeOffset), "growx");

        z.add(this.zProbeButton, "spanx 2, spany 2, growx, growy");

        z.add(new JLabel(Z_DISTANCE));
        z.add(new JSpinner(this.zProbeDistance), "growx");
        
        // SETTINGS TAB
        JPanel settings = new JPanel(new MigLayout("wrap 6"));
        settings.add(new JLabel(Localization.getString("probe.units") + ":"), "al right");
        settings.add(settingsUnits, "growx");

        settings.add(new JLabel(Localization.getString("probe.endmill-diameter") + ":"), "al right");
        settings.add(new JSpinner(settingsProbeDiameter), "growx");

        settings.add(new JLabel(Localization.getString("probe.find-rate") + ":"), "al right");
        settings.add(new JSpinner(settingsFastFindRate), "growx");

        settings.add(new JLabel("Work Coordinates:"), "al right");
        settings.add(settingsWorkCoordinate, "growx");

        settings.add(new JLabel(Localization.getString("probe.measure-rate") + ":"), "al right");
        settings.add(new JSpinner(settingsSlowMeasureRate), "growx");

        settings.add(new JLabel(Localization.getString("probe.retract-amount") + ":"), "al right");
        settings.add(new JSpinner(settingsRetractAmount), "growx");

        jtp.add(XYZ_TAB, xyz);
        jtp.add(OUTSIDE_TAB, outside);
        jtp.add(Z_TAB, z);
        //jtp.add("inside", inside);
        jtp.add(SETTINGS_TAB, settings);

        this.setLayout(new BorderLayout());
        this.add(jtp);
    }


    @Override
    public void componentOpened() {
        controlChangeListener();
    }

    @Override
    public void componentClosed() {
        if (this.active != null) {
            RenderableUtils.removeRenderable(this.active);
        }
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");

        String version = p.getProperty("version");

        ProbeSettings ps = new ProbeSettings();
        ps.xyzXDistance = get(this.xyzXDistanceModel);
        ps.xyzYDistance = get(xyzYDistanceModel);
        ps.xyzZDistance = get(xyzZDistanceModel);
        ps.xyzXOffset = get(xyzXOffsetModel);
        ps.xyzYOffset = get(xyzYOffsetModel);
        ps.xyzZOffset = get(xyzZOffsetModel);

        ps.outsideXDistance = get(this.outsideXDistanceModel);
        ps.outsideYDistance = get(outsideYDistanceModel);
        ps.outsideXOffset = get(outsideXOffsetModel);
        ps.outsideYOffset = get(outsideYOffsetModel);

        ps.zDistance = get(zProbeDistance);
        ps.zOffset = get(zProbeOffset);

        ps.insideXDistance = get(this.insideXDistanceModel);
        ps.insideYDistance = get(insideYDistanceModel);
        ps.insideXOffset = get(insideXOffsetModel);
        ps.insideYOffset = get(insideYOffsetModel);

        ps.settingsWorkCoordinateIdx = (int) settingsWorkCoordinate.getSelectedIndex();
        ps.settingsUnitsIdx = (int) settingsUnits.getSelectedIndex();
        ps.settingsProbeDiameter = get(settingsProbeDiameter);
        ps.settingsFastFindRate = get(settingsFastFindRate);
        ps.settingsSlowMeasureRate = get(settingsSlowMeasureRate);
        ps.settingsRetractAmount = get(settingsRetractAmount);

        ps.selectedTabIdx = this.jtp.getSelectedIndex();

        p.setProperty("json_data", new Gson().toJson(ps));
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");

        String jsonData = p.getProperty("json_data");
        if (jsonData == null) return;

        ProbeSettings ps = new Gson().fromJson(jsonData, ProbeSettings.class);
        xyzXDistanceModel.setValue(ps.xyzXDistance);
        xyzYDistanceModel.setValue(ps.xyzYDistance);
        xyzZDistanceModel.setValue(ps.xyzZDistance);
        xyzXOffsetModel.setValue(ps.xyzXOffset);
        xyzYOffsetModel.setValue(ps.xyzYOffset);
        xyzZOffsetModel.setValue(ps.xyzZOffset);

        outsideXDistanceModel.setValue(ps.outsideXDistance);
        outsideYDistanceModel.setValue(ps.outsideYDistance);
        outsideXOffsetModel.setValue(ps.outsideXOffset);
        outsideYOffsetModel.setValue(ps.outsideYOffset);

        zProbeDistance.setValue(ps.zDistance);
        zProbeOffset.setValue(ps.zOffset);

        insideXDistanceModel.setValue(ps.insideXDistance);
        insideYDistanceModel.setValue(ps.insideYDistance);
        insideXOffsetModel.setValue(ps.insideXOffset);
        insideYOffsetModel.setValue(ps.insideYOffset);

        settingsWorkCoordinate.setSelectedIndex(ps.settingsWorkCoordinateIdx);
        settingsUnits.setSelectedIndex(ps.settingsUnitsIdx);
        settingsProbeDiameter.setValue(ps.settingsProbeDiameter);
        settingsFastFindRate.setValue(ps.settingsFastFindRate);
        settingsSlowMeasureRate.setValue(ps.settingsSlowMeasureRate);
        settingsRetractAmount.setValue(ps.settingsRetractAmount);

        jtp.setSelectedIndex(ps.selectedTabIdx);
    }
}
