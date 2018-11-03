package torumpca.pl.gut.mt.alg;

import es.usc.citius.hipster.algorithm.Algorithm;
import es.usc.citius.hipster.algorithm.Hipster;
import es.usc.citius.hipster.model.Transition;
import es.usc.citius.hipster.model.function.impl.StateTransitionFunction;
import es.usc.citius.hipster.model.problem.ProblemBuilder;
import es.usc.citius.hipster.model.problem.SearchProblem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import torumpca.pl.gut.mt.Utils;
import torumpca.pl.gut.mt.model.*;
import torumpca.pl.gut.mt.error.DataNotAvailableException;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Created by Tomasz Rumpca on 2016-02-07.
 */
public class AStarResolver implements ProblemResolver {

    private static Logger LOG = LoggerFactory.getLogger(AStarResolver.class);

    public static final String DEGREES = "\u00b0";
    static boolean[][] mask;

    public static Collection<Point> validLocationsFrom(Point loc) {
        Collection<Point> validMoves = new HashSet<>();
        // Check for all valid movements
        for (int row = -1; row <= 1; row++) {
            for (int column = -1; column <= 1; column++) {
                try {
                    if (isFree(new Point(loc.x + column, loc.y + row))) {
                        validMoves.add(new Point(loc.x + column, loc.y + row));
                    }
                } catch (ArrayIndexOutOfBoundsException ex) {
                    // Invalid move!
                }
            }
        }
        validMoves.remove(loc);

        return validMoves;
    }

    private static boolean isFree(Point point) {
        return mask[point.x][point.y];
    }

    private Solution getSolution(Algorithm.SearchResult result) {
        long overallCost = result.getElapsed();
        List optimalPaths = result.getOptimalPaths();
        return new Solution(optimalPaths, overallCost);
    }

    private Solution getSolution(Throwable throwable) {
        //TODO implement getSolution from error
        return null;
    }

    public Solution resolve(final WindForecastModel forecast, UserData input) {

        LOG.info("Start determining the solution using A* algorithm");

        final Ship ship = input.getShip();

        final LatLon originCoordinates = new LatLon(input.getOriginLat(), input.getOriginLon());
        final LatLon goalCoordinates = new LatLon(input.getGoalLat(), input.getGoalLon());
        final Point origin, goal;
        try {
            origin = forecast.getPointFromLatLon(originCoordinates);
            goal = forecast.getPointFromLatLon(goalCoordinates);
        } catch (DataNotAvailableException e) {
            LOG.error("origin and/or destination out of range of available forecast data", e);
            return getSolution(e);
        }

        final LatLon goalLocation = forecast.getLatLonFromPoint(goal);

        final VectorComponents[][] forecastData = forecast.getForecastData();

        //mask = Masks.generateSimpleMask(forecast);
        mask = Masks.getMaskAllValid(forecast);

        //@formatter:off
        SearchProblem problemDef = ProblemBuilder
                .create()
                .initialState(origin)
                .defineProblemWithoutActions()
                .useTransitionFunction(getTransitionFunction())
                .useCostFunction(transition ->
                        costFunction(forecast, ship, forecastData, transition))
                .useHeuristicFunction((Point state) ->
                        heuristicFunction(forecast, ship, goal, goalLocation, state))
                .build();
        //@formatter:on

        Algorithm.SearchResult result = Hipster.createAStar(problemDef).search(goal);

        LOG.info("Result: \n{}", result);

        return getSolution(result);
    }

    private StateTransitionFunction<Point> getTransitionFunction() {
        return new StateTransitionFunction<Point>() {
            @Override
            public Iterable<Point> successorsOf(Point state) {
                return validLocationsFrom(state);
            }
        };
    }

    private Double costFunction(WindForecastModel forecast, Ship ship, VectorComponents[][] forecastData, Transition<Void, Point> transition) {
        final Point source = transition.getFromState();
        final Point destination = transition.getState();

        if (source.equals(destination)) {
            return Double.MAX_VALUE;
        }

        final LatLon sourceLocation = forecast.getLatLonFromPoint(source);
        final LatLon destLocation = forecast.getLatLonFromPoint(destination);
        final double distance = Utils.getDistance(sourceLocation, destLocation);

        final VectorComponents wind = forecastData[source.x][source.y];

        final double azimuth = Utils.getAzimuth(sourceLocation, destLocation);
        final double normalizedAzimuth = (azimuth + 2 * Math.PI) % (2 * Math.PI);

        final double rotationAngle = -(normalizedAzimuth - Math.PI / 2);
        final VectorComponents normalizedWindComponents =
                Utils.rotateVector(rotationAngle, wind);
        LOG.debug(
                "COST - normAzimuth {}{}, rotation {}{}, norm wind components {},"
                        + " origin wind {}",
                Math.toDegrees(normalizedAzimuth), DEGREES,
                Math.toDegrees(rotationAngle), DEGREES, normalizedWindComponents,
                wind);
        final Double cost =
                ship.calculateTravelCost(distance, normalizedWindComponents);
        LOG.debug("COST - from {} to {} cost {}", source, destination, cost);
        return cost;
    }

    private Double heuristicFunction(WindForecastModel forecast, Ship ship, Point goal, LatLon goalLocation, Point state) {
        final LatLon currentLocation = forecast.getLatLonFromPoint(state);
        final double distance = Utils.getDistance(currentLocation, goalLocation);
        final Double estimatedCost = distance / ship.getAverageSpeedInMpS() * ship
                .getAverageCostOfHourOnSea() / 3600;
        LOG.debug(
                "HEURISTIC - from {} to target in {} estimated cost {} distance {}",
                state, goal, estimatedCost, distance);
        return estimatedCost;
    }

}
