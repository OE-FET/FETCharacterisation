package Experiment;

import JISA.Addresses.*;
import JISA.Control.*;
import JISA.Devices.*;
import JISA.Experiment.*;
import JISA.GUI.*;
import JISA.Util;
import JISA.VISA.*;

/**
 * JISA Template Application.
 * <p>
 * Write the code you want to run when the application is run in the "run(...)" method.
 * <p>
 * If you are not going to use any GUI elements, remove "extends GUI" from the line below.
 */
public class Main extends GUI {

    static final double MIN_SD_VOLTAGE   = -5.0;
    static final double MAX_SD_VOLTAGE   = -60;
    static final int    STEPS_SD_VOLTAGE = 2;

    static final double MIN_G_VOLTAGE   = 0;
    static final double MAX_G_VOLTAGE   = -60;
    static final int    STEPS_G_VOLTAGE = 61;

    static final double CURRENT_LIMIT = 1e-3;
    static final int    AVERAGE_COUNT = 25;
    static final double DELAY_TIME    = 0.5;

    static final double MIN_SD_VOLTAGE_OUTPUT   = 0.0;
    static final double MAX_SD_VOLTAGE_OUTPUT   = -60.0;
    static final int    STEPS_SD_VOLTAGE_OUTPUT = 61;

    static final double MIN_GATE_VOLTAGE_OUTPUT   = 0.0;
    static final double MAX_GATE_VOLTAGE_OUTPUT   = -60.0;
    static final int    STEPS_GATE_VOLTAGE_OUTPUT = 7;

    static SetGettable<Double>  minGateT;
    static SetGettable<Double>  maxGateT;
    static SetGettable<Integer> gateStepsT;
    static SetGettable<Double>  minSDT;
    static SetGettable<Double>  maxSDT;
    static SetGettable<Integer> sdStepsT;
    static SetGettable<Double>  limitT;
    static SetGettable<Integer> countT;
    static SetGettable<Double>  delayT;
    static SetGettable<String>  fileT;
    static SetGettable<Boolean> fourProbeT;
    static ResultList           transferResults;

    static SetGettable<Double>  minGateO;
    static SetGettable<Double>  maxGateO;
    static SetGettable<Integer> gateStepsO;
    static SetGettable<Double>  minSDO;
    static SetGettable<Double>  maxSDO;
    static SetGettable<Integer> sdStepsO;
    static SetGettable<Double>  limitO;
    static SetGettable<Integer> countO;
    static SetGettable<Double>  delayO;
    static SetGettable<String>  fileO;
    static ResultList           outputResults;

    static Tabs tabs;

    static boolean stopFlag = true;

    static SMU smuSD;
    static SMU smuG;
    static SMU smu4P1;
    static SMU smu4P2;

    public static void run(String[] args) throws Exception {

//        GUI.infoAlert("Select Device", "Keithley 2450", "Please select the Keithley 2450");
//        smu1 = new K2450(GUI.browseVISA());
//
//        GUI.infoAlert("Select Device", "Keithley 236", "Please select the Keithley 236");
//        smu2 = new K236(GUI.browseVISA());

        MCSMU smu = new DummyMCSMU();

        // Treat each channel as its own SMU
        smuSD = smu.getChannel(0);
        smuG = smu.getChannel(1);
        smu4P1 = smu.getChannel(2);
        smu4P2 = smu.getChannel(3);

        // Create results storage
        transferResults = new ResultList("SD Voltage", "Gate Voltage", "Drain Current", "Leakage", "4PP 1", "4PP 2");
        transferResults.setUnits("V", "V", "A", "A", "V", "V");

        outputResults = new ResultList("SD Voltage", "Gate Voltage", "Drain Current", "Leakage");
        outputResults.setUnits("V", "V", "A", "A");

        // Create the tabs
        tabs = new Tabs("FET Characterisation");

        createTransferSection();
        createOutputSection();

        tabs.setMaximised(true);
        tabs.show();

    }

    public static void createTransferSection() throws Exception {

        // Create config panels
        Fields params = new Fields("Experiment Parameters");
        Fields config = new Fields("Configuration");

        // Results displays
        Table table = new Table("Table of Results", transferResults);
        Plot  plot  = new Plot("Transfer Curve", transferResults, 1, 2, 0);

        plot.showMarkers(false);

        // Put them all in a grid
        Grid transferGrid = new Grid("Transfer Curve", params, config, table, plot);

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

        minGateT.set(MIN_G_VOLTAGE);
        maxGateT.set(MAX_G_VOLTAGE);
        gateStepsT.set(STEPS_G_VOLTAGE);

        minSDT.set(MIN_SD_VOLTAGE);
        maxSDT.set(MAX_SD_VOLTAGE);
        sdStepsT.set(STEPS_SD_VOLTAGE);

        limitT.set(CURRENT_LIMIT);
        countT.set(AVERAGE_COUNT);
        delayT.set(DELAY_TIME);

        transferGrid.addToolbarButton("Start", Main::doTransfer);
        transferGrid.addToolbarButton("Stop", Main::stopExperiment);
        transferGrid.setNumColumns(2);
        tabs.addTab(transferGrid);

    }

    public static void createOutputSection() throws Exception {

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

        grid.addToolbarButton("Start", Main::doOutput);
        grid.addToolbarButton("Stop", Main::stopExperiment);
        grid.setNumColumns(2);

        tabs.addTab(grid);


    }

    public static void stopExperiment() {
        stopFlag = true;
    }

    public static void doTransfer() throws Exception {

        if (!stopFlag) {
            GUI.errorAlert("Error", "Experiment Running", "Another experiment is already running.\n\nPlease wait until it has finished.");
            return;
        }

        String outputFile = fileT.get();

        if (outputFile.trim().equals("")) {
            GUI.errorAlert("Error", "No Output File", "Please select a file to output to.");
            return;
        }

        stopFlag = false;

        double minVG   = minGateT.get();
        double maxVG   = maxGateT.get();
        int    stepsVG = gateStepsT.get();

        double minSDV   = minSDT.get();
        double maxSDV   = maxSDT.get();
        int    stepsSDV = sdStepsT.get();

        double currentLimit = limitT.get();
        int    averageCount = countT.get();
        int    delayMSec    = (int) (delayT.get() * 1000);

        boolean useFourProbe = fourProbeT.get();

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

        smu4P1.setSource(SMU.Source.CURRENT);
        smu4P1.setVoltageRange(2.0 * maxSDV);
        smu4P1.setVoltageLimit(2.0 * maxSDV);
        smu4P1.setAverageMode(SMU.AMode.MEAN_REPEAT);
        smu4P1.setAverageCount(averageCount);
        smu4P1.useFourProbe(false);
        smu4P1.setCurrent(0);

        smu4P2.setSource(SMU.Source.CURRENT);
        smu4P2.setVoltageRange(2.0 * maxSDV);
        smu4P2.setVoltageLimit(2.0 * maxSDV);
        smu4P2.setAverageMode(SMU.AMode.MEAN_REPEAT);
        smu4P2.setAverageCount(averageCount);
        smu4P2.useFourProbe(false);
        smu4P2.setCurrent(0);

        smuSD.turnOn();
        smuG.turnOn();

        if (useFourProbe) {
            smu4P1.turnOn();
            smu4P2.turnOn();
        }

        mainLoop:
        for (double VSD : sdVoltages) {

            smuSD.setVoltage(VSD);

            for (double VG : gateVoltages) {

                smuG.setVoltage(VG);
                Thread.sleep(delayMSec);

                transferResults.addData(
                        VSD,
                        VG,
                        smuSD.getCurrent(),
                        smuG.getCurrent(),
                        useFourProbe ? smu4P1.getVoltage() : 0,
                        useFourProbe ? smu4P2.getVoltage() : 0
                );

                if (stopFlag) {
                    break mainLoop;
                }

            }

        }

        smuSD.turnOff();
        smuG.turnOff();
        smu4P1.turnOff();
        smu4P2.turnOff();

        transferResults.output(outputFile);
        stopFlag = true;


    }

    public static void doOutput() throws Exception {

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

    }

    public static void main(String[] args) {

        try {
            run(args);
        } catch (Exception e) {
            Util.exceptionHandler(e);
        }

    }
}
