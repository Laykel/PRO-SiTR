/*
 * Filename : Simulation.java
 * Creation date : 07.04.2019
 */

package ch.heigvd.sitr.model;

import ch.heigvd.sitr.gui.simulation.Displayer;
import ch.heigvd.sitr.gui.simulation.SimulationWindow;
import ch.heigvd.sitr.map.RoadNetwork;
import ch.heigvd.sitr.map.RoadSegment;
import ch.heigvd.sitr.map.input.OpenDriveHandler;
import ch.heigvd.sitr.statistics.Statistics;
import ch.heigvd.sitr.vehicle.ItineraryPath;
import ch.heigvd.sitr.vehicle.Vehicle;
import ch.heigvd.sitr.vehicle.VehicleController;
import lombok.Getter;
import lombok.Setter;

import javax.xml.transform.stream.StreamSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simulation class handles all global simulation settings and values
 * <p>
 * The main simulation loop runs here as well
 *
 * @author Luc Wachter, Simon Walther
 */
public class Simulation {
    private static final Logger LOG = Logger.getLogger(Simulation.class.getName());

    // Rate at which the redrawing will happen in milliseconds
    private static final int UPDATE_RATE = 40;

    // The displayable component we need to repaint
    private Displayer window;

    // The scenario of the current simulation
    @Getter
    private Scenario scenario;
    // The behaviour the vehicles should have when arriving at their destination
    @Getter
    private VehicleBehaviour behaviour;
    // List of vehicles generated by traffic generator
    @Getter
    private ArrayList<Vehicle> vehicles;
    // Road network
    private final RoadNetwork roadNetwork;

    // The timer for the main loop
    private Timer timer;

    // Default rate of calculation for the simulation
    @Getter
    private final double defaultDeltaT = 0.15;
    // Effective rate of calculation for the simulation
    @Getter
    @Setter
    private double deltaT = defaultDeltaT;

    // Object for making statistics
    @Getter
    private final Statistics stats;

    /**
     * Simulation constructor
     *
     * @param scenario    The scenario the simulation must create
     * @param behaviour   The behaviour the vehicles must adopt when arriving at their destination
     * @param controllers The number of vehicles for each controller type
     */
    public Simulation(Scenario scenario, VehicleBehaviour behaviour,
                      HashMap<VehicleControllerType, Integer> controllers) {
        this.scenario = scenario;
        this.behaviour = behaviour;

        // Create a roadNetwork instance and then parse the OpenDRIVE XML file
        roadNetwork = new RoadNetwork();

        // Load road network
        parseOpenDriveXml(roadNetwork, scenario.getConfigPath());

        // Generate vehicles from user parameters
        vehicles = generateTraffic(controllers);

        // Create the statistic for this simulation
        stats = new Statistics(vehicles, roadNetwork, 1);
    }

    /**
     * Sets up simulation, then launches main loop
     */
    public void loop() {
        // Launch main window
        window = SimulationWindow.getInstance();

        // Print the road network
        roadNetwork.draw(scenario.getScale());

        // Start the statistics
        stats.start();

        // Start main simulation loop
        startLoop();
    }

    /**
     * Main simulation loop, runs in a fixed rate timer loop
     */
    public void startLoop() {
        // Create a timer to run the main loop
        timer = new Timer();

        // Schedule a task to run immediately, and then
        // every UPDATE_RATE per second
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                for (Vehicle vehicle : vehicles) {
                    vehicle.update(deltaT);
                    vehicle.draw(scenario.getScale());

                    // DEBUG
                    System.out.println(vehicle);

                    // Notify observers that the vehicle parameters have changed
                    vehicle.notifyObservers();
                }

                // Callback to paintComponent()
                window.repaint();
            }
        }, 0, UPDATE_RATE);
    }

    /**
     * Method used to stop the timer. it is used when we close the current simulation
     */
    public void stopLoop() {
        timer.cancel();
    }

    /**
     * Generate correct number of vehicle for each specified controller
     *
     * @param controllers The hash map containing the specified number of vehicles for each controller
     * @return a list of all vehicles in the simulation
     */
    private ArrayList<Vehicle> generateTraffic(HashMap<VehicleControllerType, Integer> controllers) {
        ArrayList<Vehicle> vehicles = new ArrayList<>();

        // Create itineraries
        LinkedList<ItineraryPath> defaultItinerary = new LinkedList<>();

        for (RoadSegment roadSegment : roadNetwork) {
            defaultItinerary.add(new ItineraryPath(roadSegment, scenario.getScale()));
        }
        // TODO: create several itineraries and randomize

        // Iterate through the hash map
        controllers.forEach((key, value) -> {
            // One controller for all vehicles of a given type
            VehicleController controller = new VehicleController(key);

            // Generate as many vehicles as asked
            for (int i = 0; i < value; i++) {
                Vehicle v = new Vehicle("regular.xml", controller, defaultItinerary);
                vehicles.add(v);
            }
        });

        // Randomize vehicles order
        Collections.shuffle(vehicles);

        // Set vehicles positions and front vehicles
        int c = 0;
        Vehicle previousVehicle = null;
        for (Vehicle vehicle : vehicles) {
            // Place vehicles at a good distance from each other
            vehicle.setPosition(vehicle.getPosition() + (vehicle.getLength() * 3 * c++));

            // Set front vehicle (if there is one)
            if (previousVehicle != null) {
                previousVehicle.setFrontVehicle(vehicle);
            }

            previousVehicle = vehicle;
        }

        // Set last vehicle's front vehicle
        if (previousVehicle != null) {
            previousVehicle.setFrontVehicle(vehicles.get(0));
        }

        return vehicles;
    }

    /**
     * Parse the OpenDrive XML file
     *
     * @param roadNetwork       The Road network that will contains OpenDrive road network
     * @param openDriveFilename The OpenDrive filename
     */
    private void parseOpenDriveXml(RoadNetwork roadNetwork, String openDriveFilename) {
        LOG.log(Level.INFO, "parsing {0} file", openDriveFilename);
        InputStream in = getClass().getResourceAsStream(openDriveFilename);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        OpenDriveHandler.loadRoadNetwork(roadNetwork, new StreamSource(br));
    }
}
