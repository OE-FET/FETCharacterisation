package Experiment;

import JISA.Addresses.*;
import JISA.Control.*;
import JISA.Devices.*;
import JISA.Experiment.*;
import JISA.GUI.*;
import JISA.Util;
import JISA.VISA.*;

import java.io.IOException;

/**
 * JISA Template Application.
 * <p>
 * Write the code you want to run when the application is run in the "run(...)" method.
 * <p>
 * If you are not going to use any GUI elements, remove "extends GUI" from the line below.
 */
public class Main extends GUI {

    private static final double MIN_SD_VOLTAGE   = -5.0;
    private static final double MAX_SD_VOLTAGE   = -60;
    private static final int    STEPS_SD_VOLTAGE = 2;

    private static final double MIN_G_VOLTAGE   = 0;
    private static final double MAX_G_VOLTAGE   = -60;
    private static final int    STEPS_G_VOLTAGE = 61;

    private static final double CURRENT_LIMIT = 1e-3;
    private static final int    AVERAGE_COUNT = 25;
    private static final double DELAY_TIME    = 0.5;

    private static final double MIN_SD_VOLTAGE_OUTPUT   = 0.0;
    private static final double MAX_SD_VOLTAGE_OUTPUT   = -60.0;
    private static final int    STEPS_SD_VOLTAGE_OUTPUT = 61;

    private static final double MIN_GATE_VOLTAGE_OUTPUT   = 0.0;
    private static final double MAX_GATE_VOLTAGE_OUTPUT   = -60.0;
    private static final int    STEPS_GATE_VOLTAGE_OUTPUT = 7;

    private static SetGettable<Double>  minGateT;
    private static SetGettable<Double>  maxGateT;
    private static SetGettable<Integer> gateStepsT;
    private static SetGettable<Double>  minSDT;
    private static SetGettable<Double>  maxSDT;
    private static SetGettable<Integer> sdStepsT;
    private static SetGettable<Double>  limitT;
    private static SetGettable<Integer> countT;
    private static SetGettable<Double>  delayT;
    private static SetGettable<String>  fileT;
    private static SetGettable<Boolean> fourProbeT;
    private static ResultList           transferResults;

    private static SetGettable<Double>  minGateO;
    private static SetGettable<Double>  maxGateO;
    private static SetGettable<Integer> gateStepsO;
    private static SetGettable<Double>  minSDO;
    private static SetGettable<Double>  maxSDO;
    private static SetGettable<Integer> sdStepsO;
    private static SetGettable<Double>  limitO;
    private static SetGettable<Integer> countO;
    private static SetGettable<Double>  delayO;
    private static SetGettable<String>  fileO;
    private static ResultList           outputResults;

    private static Tabs tabs;

    private static boolean stopFlag = true;

    private static SetGettable<SMU> smu1;
    private static SetGettable<SMU> smu2;
    private static SetGettable<SMU> smu3;
    private static SetGettable<SMU> smu4;

    private static SMU smuSD  = null;
    private static SMU smuG   = null;
    private static SMU smu4P1 = null;
    private static SMU smu4P2 = null;

    /**
     * Runs at start, this is where it all begins.
     *
     * @throws Exception Upon something going wrong
     */
    private static void run(String[] args) throws Exception {

        // Create results storage
        transferResults = new ResultList("SD Voltage", "Gate Voltage", "Drain Current", "Leakage", "4PP 1", "4PP 2");
        transferResults.setUnits("V", "V", "A", "A", "V", "V");

        outputResults = new ResultList("SD Voltage", "Gate Voltage", "Drain Current", "Leakage");
        outputResults.setUnits("V", "V", "A", "A");

        // Create the tabs
        tabs = new Tabs("FET Characterisation");

        // Create each section of our GUI
        createTransferSection();
        createOutputSection();
        createConnectionSection();
        createConfigSection();

        // Make sure the window is maximised and show it
        tabs.setMaximised(true);
        tabs.show();

    }

    /**
     * Creates the "Transfer Curve" tab in the GUI, for controlling and observing transfer curve measurements.
     *
     * @throws Exception Upon something going wrong
     */
    private static void createTransferSection() throws Exception {

        // Create config panels
        Fields params = new Fields("Experiment Parameters");
        Fields config = new Fields("Configuration");

        // Results displays
        Table table = new Table("Table of Results", transferResults);
        Plot  plot  = new Plot("Transfer Curve", transferResults, 1, 2, 0);

        plot.showMarkers(false);

        // Put them all in a grid
        Grid transferGrid = new Grid("Transfer Curve", params, config, table, plot);

        // Add fields to panels, returning SetGettable objects which allow use to query and set the value in each field
        minGateT = params.addDoubleField("Min Gate [V]");
        maxGateT = params.addDoubleField("Max Gate [V]");
        gateStepsT = params.addIntegerField("No. Steps");

        minSDT = params.addDoubleField("Min SD [V]");
        maxSDT = params.addDoubleField("Max SD [V]");
        sdStepsT = params.addIntegerField("No. Steps");

        limitT = config.addDoubleField("Current Limit [A]");
        countT = config.addIntegerField("Averaging Count");
        delayT = config.addDoubleField("Delay Time [s]");
        fileT = config.addFileSave("Output File");
        fourProbeT = config.addCheckBox("Four Probe Measurement?");

        // Set the default values
        minGateT.set(MIN_G_VOLTAGE);
        maxGateT.set(MAX_G_VOLTAGE);
        gateStepsT.set(STEPS_G_VOLTAGE);

        minSDT.set(MIN_SD_VOLTAGE);
        maxSDT.set(MAX_SD_VOLTAGE);
        sdStepsT.set(STEPS_SD_VOLTAGE);

        limitT.set(CURRENT_LIMIT);
        countT.set(AVERAGE_COUNT);
        delayT.set(DELAY_TIME);

        // Add toolbar buttons
        transferGrid.addToolbarButton("Start Transfer", Main::doTransfer);
        transferGrid.addToolbarButton("Stop Experiment", Main::stopExperiment);
        transferGrid.addToolbarButton("Clear", transferResults::clear);

        transferGrid.setNumColumns(2);
        tabs.addTab(transferGrid);

    }

    /**
     * Creates the "Output Curve" tab in the GUI, for controlling and observing output curve measurements.
     *
     * @throws Exception Upon something going wrong
     */
    private static void createOutputSection() throws Exception {

        // Create config panels
        Fields params = new Fields("Experiment Parameters");
        Fields config = new Fields("Configuration");

        minGateO = params.addDoubleField("Min Gate [V]");
        maxGateO = params.addDoubleField("Max Gate [V]");
        gateStepsO = params.addIntegerField("No. Steps");

        minSDO = params.addDoubleField("Min SD [V]");
        maxSDO = params.addDoubleField("Max SD [V]");
        sdStepsO = params.addIntegerField("No. Steps");

        limitO = config.addDoubleField("Current Limit [A]");
        countO = config.addIntegerField("Averaging Count");
        delayO = config.addDoubleField("Delay Time [s]");
        fileO = config.addFileSave("Output File");

        minGateO.set(MIN_GATE_VOLTAGE_OUTPUT);
        maxGateO.set(MAX_GATE_VOLTAGE_OUTPUT);
        gateStepsO.set(STEPS_GATE_VOLTAGE_OUTPUT);

        minSDO.set(MIN_SD_VOLTAGE_OUTPUT);
        maxSDO.set(MAX_SD_VOLTAGE_OUTPUT);
        sdStepsO.set(STEPS_SD_VOLTAGE_OUTPUT);

        limitO.set(CURRENT_LIMIT);
        countO.set(AVERAGE_COUNT);
        delayO.set(DELAY_TIME);

        Table table = new Table("Table of Results", outputResults);
        Plot  plot  = new Plot("Output Curve", outputResults, 0, 2, 1);
        Grid  grid  = new Grid("Output Curve", params, config, table, plot);

        plot.showMarkers(false);
        grid.addToolbarButton("Start Output", Main::doOutput);
        grid.addToolbarButton("Stop Experiment", Main::stopExperiment);
        grid.addToolbarButton("Clear", outputResults::clear);

        grid.setNumColumns(2);

        tabs.addTab(grid);


    }

    /**
     * Creates the "Connection Config" tab in the GUI for configuring how to connect to each instrument.
     */
    private static void createConnectionSection() {

        // Create a ConfigGrid - a grid of Instrument Connection configuration panels
        ConfigGrid grid = new ConfigGrid("Connection Config");
        grid.setNumColumns(2);

        // Add instruments to configure, returns GetSettable objects for the relevant instrument objects
        smu1 = grid.addInstrument("SMU 1", SMU.class);
        smu2 = grid.addInstrument("SMU 2", SMU.class);
        smu3 = grid.addInstrument("SMU 3", SMU.class);
        smu4 = grid.addInstrument("SMU 4", SMU.class);

        // Add this section to the tabs
        tabs.addTab(grid);

    }

    /**
     * Creates the "Instrument Config" tab in the GUI for configuring which SMU channel should be used for what.
     */
    private static void createConfigSection() throws Exception {

        // Create panel for Source-Drain configuration
        Fields               sourceDrain = new Fields("Source-Drain SMU");
        SetGettable<Integer> sdSMU       = sourceDrain.addChoice("SMU", new String[]{"SMU 1", "SMU 2", "SMU 3", "SMU 4"});
        SetGettable<Integer> sdChannel   = sourceDrain.addIntegerField("Channel Number");

        // Set default value
        sdSMU.set(0);

        // Add "Apply" button
        sourceDrain.addButton("Apply", () -> smuSD = applyButton(sdSMU, sdChannel));

        // Rinse-and-repeat
        Fields               sourceGate = new Fields("Source-Gate SMU");
        SetGettable<Integer> sgSMU      = sourceGate.addChoice("SMU", new String[]{"SMU 1", "SMU 2", "SMU 3", "SMU 4"});
        SetGettable<Integer> sgChannel  = sourceGate.addIntegerField("Channel Number");

        sgSMU.set(1);

        sourceGate.addButton("Apply", () -> smuG = applyButton(sgSMU, sgChannel));


        Fields               fourPoint1 = new Fields("Four-Point-Probe 1");
        SetGettable<Integer> fp1SMU     = fourPoint1.addChoice("SMU", new String[]{"SMU 1", "SMU 2", "SMU 3", "SMU 4"});
        SetGettable<Integer> fp1Channel = fourPoint1.addIntegerField("Channel Number");

        fp1SMU.set(2);

        fourPoint1.addButton("Apply", () -> smu4P1 = applyButton(fp1SMU, fp1Channel));

        Fields               fourPoint2 = new Fields("Four-Point-Probe 2");
        SetGettable<Integer> fp2SMU     = fourPoint2.addChoice("SMU", new String[]{"SMU 1", "SMU 2", "SMU 3", "SMU 4"});
        SetGettable<Integer> fp2Channel = fourPoint2.addIntegerField("Channel Number");

        fp2SMU.set(3);

        fourPoint2.addButton("Apply", () -> smu4P2 = applyButton(fp2SMU, fp2Channel));

        Grid grid = new Grid("Instrument Config", sourceDrain, sourceGate, fourPoint1, fourPoint2);
        grid.setNumColumns(2);

        tabs.addTab(grid);

    }

    private static SMU applyButton(SetGettable<Integer> smuI, SetGettable<Integer> channelI) throws Exception {

        // Store SMUs in array for easy access
        SetGettable<SMU>[] SMUs = new SetGettable[]{smu1, smu2, smu3, smu4};

        if (smuI.get() < 0 || smuI.get() > 3) {
            GUI.errorAlert("Error", "Select SMU", "Please select an SMU.");
            return null;
        }

        SMU smu = SMUs[smuI.get()].get();

        if (smu == null) {
            GUI.errorAlert("Error", "Not Connected", "That SMU is not connected!");
            return null;
        }

        int channel = channelI.get();

        if (smu instanceof MCSMU) {

            if (channel >= ((MCSMU) smu).getNumChannels() || channel < 0) {
                GUI.errorAlert("Error", "Invalid Channel", "That SMU does not have that channel.");
                return null;
            }

            return ((MCSMU) smu).getChannel(channel);

        } else {
            return smu;
        }
    }

    /**
     * Notifies all experiments to stop running
     */
    private static void stopExperiment() {

        if (stopFlag) {
            GUI.errorAlert("Warning", "Nothing to Stop", "There is nothing running to stop.");
        }

        stopFlag = true;
    }

    /**
     * Performs a transfer curve characterisation, outputting the data to a CSV file.
     *
     * @throws Exception Upon something going wrong
     */
    private static void doTransfer() throws Exception {

        boolean useFourProbe = fourProbeT.get();

        if (smuSD == null || smuG == null) {
            GUI.errorAlert("Error", "Not Configured", "Source-Drain and Source-Gate SMUs are not configured.");
            return;
        }

        if ((smu4P1 == null || smu4P2 == null) && useFourProbe) {
            GUI.errorAlert("Error", "Four-Probe Not Configured", "To perform four-point-probe measurements, the 4PP SMUs must be configured.");
            return;
        }

        // If stopFlag == false then another experiment must be currently running
        if (!stopFlag) {
            GUI.errorAlert("Error", "Experiment Running", "Another experiment is already running.\n\nPlease wait until it has finished.");
            return;
        }

        // Get the value currently in the "Output File" text-box
        String outputFile = fileT.get();

        // Check that it isn't blank
        if (outputFile.trim().equals("")) {
            GUI.errorAlert("Error", "No Output File", "Please select a file to output to.");
            return;
        }

        // Set stopFlag to false so that the rest of the programme knows we're running
        stopFlag = false;

        // Get the values currently entered into the various parameter text-boxes
        double minVG   = minGateT.get();
        double maxVG   = maxGateT.get();
        int    stepsVG = gateStepsT.get();

        double minSDV   = minSDT.get();
        double maxSDV   = maxSDT.get();
        int    stepsSDV = sdStepsT.get();

        double currentLimit = limitT.get();
        int    averageCount = countT.get();
        int    delayMSec    = (int) (delayT.get() * 1000); // Convert to milli-seconds

        // Create arrays of the voltages we are going to use
        double[] gateVoltages = Util.makeLinearArray(minVG, maxVG, stepsVG);
        double[] sdVoltages   = Util.makeLinearArray(minSDV, maxSDV, stepsSDV);

        // Configure the Source-Drain SMU channel
        smuSD.setSource(SMU.Source.VOLTAGE);
        smuSD.setVoltageRange(2.0 * maxSDV);
        smuSD.setVoltageLimit(2.0 * maxSDV);
        smuSD.useAutoCurrentRange();            // May need to change this
        smuSD.setCurrentLimit(currentLimit);
        smuSD.setVoltage(minSDV);
        smuSD.setAverageMode(SMU.AMode.MEAN_REPEAT);
        smuSD.setAverageCount(averageCount);
        smuSD.useFourProbe(false);

        // Configure the Gate SMU channel
        smuG.setSource(SMU.Source.VOLTAGE);
        smuG.setVoltageRange(2.0 * maxVG);
        smuG.setVoltageLimit(2.0 * maxVG);
        smuG.useAutoCurrentRange();            // May need to change this
        smuG.setCurrentLimit(currentLimit);
        smuG.setVoltage(minVG);
        smuG.setAverageMode(SMU.AMode.MEAN_REPEAT);
        smuG.setAverageCount(averageCount);
        smuG.useFourProbe(false);

        if (useFourProbe) {
            // Configure the 4PP-1 SMU channel to act as a voltmeter
            smu4P1.setSource(SMU.Source.CURRENT);
            smu4P1.setVoltageRange(2.0 * maxSDV);
            smu4P1.setVoltageLimit(2.0 * maxSDV);
            smu4P1.setAverageMode(SMU.AMode.MEAN_REPEAT);
            smu4P1.setAverageCount(averageCount);
            smu4P1.useFourProbe(false);
            smu4P1.setCurrent(0);

            // Configure the 4PP-2 SMU channel to act as a voltmeter
            smu4P2.setSource(SMU.Source.CURRENT);
            smu4P2.setVoltageRange(2.0 * maxSDV);
            smu4P2.setVoltageLimit(2.0 * maxSDV);
            smu4P2.setAverageMode(SMU.AMode.MEAN_REPEAT);
            smu4P2.setAverageCount(averageCount);
            smu4P2.useFourProbe(false);
            smu4P2.setCurrent(0);

            // Enable voltage probes (SMU channels)
            smu4P1.turnOn();
            smu4P2.turnOn();
        }

        // Enable the SMU channels
        smuSD.turnOn();
        smuG.turnOn();

        mainLoop:
        for (double VSD : sdVoltages) {

            smuSD.setVoltage(VSD);

            for (double VG : gateVoltages) {

                smuG.setVoltage(VG);

                // Wait our delay time before measuring
                Thread.sleep(delayMSec);

                transferResults.addData(
                        VSD,
                        VG,
                        smuSD.getCurrent(),
                        smuG.getCurrent(),
                        useFourProbe ? smu4P1.getVoltage() : 0,
                        useFourProbe ? smu4P2.getVoltage() : 0
                );

                // If the stopFlag changes to true, then someone has pressed the Stop button
                if (stopFlag) {
                    break mainLoop; // Break out of mainLoop:
                }

            }

        }

        // Make sure all channels are turned back off
        smuSD.turnOff();
        smuG.turnOff();
        smu4P1.turnOff();
        smu4P2.turnOff();

        // Output our data as a CSV file
        transferResults.output(outputFile);

        // Reset the stopFlat
        stopFlag = true;

        // Tell the user we're done
        GUI.infoAlert("Complete", "Measurement Complete", "Transfer curve completed, data saved to:\n" + outputFile, 600);

    }

    /**
     * Performs an output curve characterisation, outputting the data to a CSV file.
     *
     * @throws Exception Upon something going wrong
     */
    private static void doOutput() throws Exception {

        if (smuSD == null || smuG == null) {
            GUI.errorAlert("Error", "Not Configured", "Source-Drain and Source-Gate SMUs are not configured.");
            return;
        }

        if (!stopFlag) {
            GUI.errorAlert("Error", "Experiment Running", "Another experiment is already running.\n\nPlease wait until it has finished.");
            return;
        }

        String outputFile = fileO.get();

        if (outputFile.trim().equals("")) {
            GUI.errorAlert("Error", "No Output File", "Please select a file to output to.");
            return;
        }

        stopFlag = false;

        double minVG   = minGateO.get();
        double maxVG   = maxGateO.get();
        int    stepsVG = gateStepsO.get();

        double minSDV   = minSDO.get();
        double maxSDV   = maxSDO.get();
        int    stepsSDV = sdStepsO.get();

        double currentLimit = limitO.get();
        int    averageCount = countO.get();
        int    delayMSec    = (int) (delayO.get() * 1000);

        double[] gateVoltages = Util.makeLinearArray(minVG, maxVG, stepsVG);
        double[] sdVoltages   = Util.makeLinearArray(minSDV, maxSDV, stepsSDV);

        smuSD.setSource(SMU.Source.VOLTAGE);
        smuSD.setVoltageRange(2.0 * maxSDV);
        smuSD.setVoltageLimit(2.0 * maxSDV);
        smuSD.useAutoCurrentRange();
        smuSD.setCurrentLimit(currentLimit);
        smuSD.setVoltage(minSDV);
        smuSD.setAverageMode(SMU.AMode.MEAN_REPEAT);
        smuSD.setAverageCount(averageCount);
        smuSD.useFourProbe(false);

        smuG.setSource(SMU.Source.VOLTAGE);
        smuG.setVoltageRange(2.0 * maxVG);
        smuG.setVoltageLimit(2.0 * maxVG);
        smuG.useAutoCurrentRange();
        smuG.setCurrentLimit(currentLimit);
        smuG.setVoltage(minVG);
        smuG.setAverageMode(SMU.AMode.MEAN_REPEAT);
        smuG.setAverageCount(averageCount);
        smuG.useFourProbe(false);

        smuSD.turnOn();
        smuG.turnOn();

        mainLoop:
        for (double VG : gateVoltages) {

            smuG.setVoltage(VG);

            for (double VSD : sdVoltages) {

                smuSD.setVoltage(VSD);
                Thread.sleep(delayMSec);

                outputResults.addData(
                        VSD,
                        VG,
                        smuSD.getCurrent(),
                        smuG.getCurrent()
                );

                if (stopFlag) {
                    break mainLoop;
                }

            }

        }

        smuSD.turnOff();
        smuG.turnOff();

        outputResults.output(outputFile);
        stopFlag = true;

        GUI.infoAlert("Complete", "Measurement Complete", "Output curve completed, data saved to:\n" + outputFile, 600);

    }

    public static void main(String[] args) {

        try {
            run(args);
        } catch (Exception e) {
            GUI.errorAlert("Exception", "Exception Thrown", e.getMessage());
            Util.exceptionHandler(e);
        }

    }
}
