package revisedstrat;

import battlecode.common.*;
import scoutjackrush.RobotPlayer;

import java.util.ArrayList;
import java.util.Arrays;

import static revisedstrat.Utils.randomDirection;

/**
 * Created by patil215 on 1/12/17.
 */
public abstract class RobotLogic {

	public RobotController rc;

	private static int ARCHON_IGNORE_ROUND = 200;

	private static boolean isLeftUnit;

	public RobotLogic(RobotController rc) {
		this.rc = rc;
		isLeftUnit = Math.random() > .5;
	}

	public abstract void run();

	public Team getEnemyTeam() {
		return rc.getTeam().opponent();
	}

	public MapLocation getRandomEnemyInitialArchonLocation() {
		MapLocation[] enemyLocs = rc.getInitialArchonLocations(getEnemyTeam());
		return enemyLocs[(int) (enemyLocs.length * (Math.random()))];
	}

	/*
	 * This method takes in a direction and attempts to move in this direction.
	 * If it cannot, then new directions are tried. By the end of this method,
	 * either the robot has moved in the returned angle, or no movable angle was
	 * found.
	 */
	public Direction moveWithRandomBounce(Direction move) throws GameActionException {
		if (rc.canMove(move)) {
			rc.move(move);
		} else {
			for (int count = 0; count < 20 && !rc.canMove(move); count++) {
				move = randomDirection();
			}
			if (rc.canMove(move)) {
				rc.move(move);
			}
		}
		return move;
	}

	/*
	 * If it is possible to move towards the target, then this method returns
	 * the best angle to do so with. Otherwise, null is returned. The method
	 * will also disallow angles that will result in the robot getting hit by a
	 * bullet.
	 */
	public Direction moveTowards(MapLocation destination) {
		Direction toMove = rc.getLocation().directionTo(destination);
		return moveTowards(toMove);
	}

	/*
	 * This method returns the bullets that will hit the player in some location.
	 * Returns null if no bullet will hit the target.
	 */
	protected BulletInfo[] getAllTargetingBullets(BulletInfo[] bullets, MapLocation location) {
		ArrayList<BulletInfo> targetingBullets = new ArrayList<>();
		RobotInfo player = new RobotInfo(-1, null, rc.getType(), location, 1, 1, 1);

		for (BulletInfo bullet : bullets) {
			if (getIntersectionDistance(bullet.location, bullet.dir, player) != -1) {
				targetingBullets.add(bullet);
			}
		}

		BulletInfo[] bulletArray = new BulletInfo[targetingBullets.size()];
		return targetingBullets.toArray(bulletArray);
	}

	/*
	 * If it is possible to move towards the specified direction, then this
	 * method returns the best angle to do so with. Otherwise, null is returned.
	 * The method will also disallow angles that will result in the robot
	 * getting hit by a bullet.
	 */
	public Direction moveTowards(Direction toMove) {
		if (rc.canMove(toMove)) {
			return toMove;
		} else {
			BulletInfo[] bullets = rc.senseNearbyBullets();
			for (int deltaAngle = 0; deltaAngle < 360; deltaAngle += 10) {
				if (isLeftUnit) {
					Direction leftDir = toMove.rotateLeftDegrees(deltaAngle);
					if (rc.canMove(leftDir)
							&& !willGetHitByABullet(rc.getLocation().add(leftDir, rc.getType().strideRadius),
									bullets)) {
						return leftDir;
					}
				} else {
					Direction rightDir = toMove.rotateRightDegrees(deltaAngle);
					if (rc.canMove(rightDir)
							&& !willGetHitByABullet(rc.getLocation().add(rightDir, rc.getType().strideRadius),
									bullets)) {
						return rightDir;
					}
				}
			}
		}
		return null;
	}

	/*
	 * This method should be called at the end of each run statement, if
	 * bytecode can be afforded. It will find a tree that can be shaked and
	 * shake it.
	 */
	protected void tryAndShakeATree() throws GameActionException {
		if (rc.canShake()) {
			TreeInfo[] trees = rc.senseNearbyTrees();
			for (TreeInfo t : trees) {
				if (rc.canShake(t.ID)) {
					rc.shake(t.ID);
					break;
				}
			}
		}
	}

	/*
	 * This method takes the location and direction of a bullet.
	 * 
	 * It returns the team of the first object that it will hit. If no object
	 * will be hit, this method returns NEUTRAL.
	 */
	public Team getFirstHitTeam(MapLocation location, Direction direction) {

		// Detect tree collisions.
		TreeInfo[] trees = rc.senseNearbyTrees();

		float minTreeDistance = Float.MAX_VALUE;
		TreeInfo hitTree = null;

		for (TreeInfo tree : trees) {
			float distance = getIntersectionDistance(location, direction, tree);

			if (distance < minTreeDistance && distance != Float.NEGATIVE_INFINITY) {
				hitTree = tree;
				minTreeDistance = distance;
			}
		}

		// Detect robot collisions
		RobotInfo[] robots = rc.senseNearbyRobots();

		float minRobotDistance = Float.MAX_VALUE;
		RobotInfo hitRobot = null;

		for (RobotInfo robot : robots) {
			float distance = getIntersectionDistance(location, direction, robot);

			if (distance < minRobotDistance && distance != Float.NEGATIVE_INFINITY) {
				hitRobot = robot;
				minRobotDistance = distance;
			}
		}

		// If nothing is hit, return neutral
		if (hitTree == null && hitRobot == null) {
			return Team.NEUTRAL;
		}

		// If only robots are intersected, return the nearest robot's team
		else if (hitTree == null && hitRobot != null) {
			return hitRobot.getTeam();
		}

		// If only trees are intersected, return the nearest tree's team
		else if (hitTree != null && hitRobot == null) {
			return Team.NEUTRAL;
		}

		// If both are intersected, return the team of whichever is closer
		else {
			if (minTreeDistance < minRobotDistance) {
				return Team.NEUTRAL;
			} else {
				return hitRobot.getTeam();
			}
		}
	}

	/*
	 * This method returns a bullet that will hit the player in its current
	 * position. If multiple bullets will hit the target, only the closest
	 * bullet is returned. Returns null if no bullet will hit the target.
	 */
	protected BulletInfo getTargetingBullet(BulletInfo[] bullets) {
		RobotInfo player = new RobotInfo(-1, null, rc.getType(), rc.getLocation(), 1, 1, 1);

		float minDistance = Float.MAX_VALUE;
		BulletInfo closestBullet = null;

		for (BulletInfo bullet : bullets) {
			float distance = rc.getLocation().distanceTo(bullet.getLocation());
			if (distance < minDistance && getIntersectionDistance(bullet.location, bullet.dir, player) != -1) {
				closestBullet = bullet;
				minDistance = distance;
			}
		}
		return closestBullet;
	}


	/**
	 * Returns whether a bullet will hit a player at a specific location.
	 */
	protected boolean willHit(BulletInfo bullet, RobotInfo player) {
		return getIntersectionDistance(bullet.location, bullet.dir, player) != -1;
	}

	/*
	 * This method determines if a character will be hit by any bullet if it
	 * moves to a particular location. The method takes in the location that the
	 * player wants to move to and an array representing all sensed bullets.
	 * 
	 * The method returns true iff a bullet will hit the player in the next
	 * round, given that the player moves to the specified location
	 */
	protected boolean willGetHitByABullet(MapLocation playerLocation, BulletInfo[] bullets) {
		RobotInfo player = new RobotInfo(-1, null, rc.getType(), playerLocation, 1, 1, 1);
		for (BulletInfo bullet : bullets) {
			if (getIntersectionDistance(bullet.location, bullet.dir, player) != -1) {
				return true;
			}
		}
		return false;
	}

	/*
	 * This method takes the location and direction of a bullet, as well as the
	 * BodyInfo of the target to be intersected.
	 * 
	 * The method returns the distance at which the target will be intersected.
	 * If the target is never intersected, this method returns -1.
	 */
	private float getIntersectionDistance(MapLocation location, Direction direction, BodyInfo target) {

		float targetRadius = target.getRadius();

		// The x and y coordinates of the center of the target.
		float xTarget = target.getLocation().x;
		float yTarget = target.getLocation().y;

		// The x and y coordinates of the bullet's starting point.
		float xStart = location.x;
		float yStart = location.y;

		// Compute the shortest distance between the bullet and the center of
		// the target
		float angle = direction.radians;
		float dist = (float) Math.abs(Math.sin(angle) * (xTarget - xStart) - Math.cos(angle) * (yTarget - yStart));

		// If the shortest distance is too large, the bullet won't ever
		// intersect the target
		if (dist > targetRadius) {
			return -Float.NEGATIVE_INFINITY;
		}

		// Compute the distance the bullet travels to get to the point of
		// closest approach
		float lengthToClosestApproach = (float) Math
				.sqrt((xTarget - xStart) * (xTarget - xStart) + (yTarget - yStart) * (yTarget - yStart) - dist * dist);

		// Compute the distance the bullet travels from the intersection point
		// to the closest approach
		float excessDistance = (float) Math.sqrt(targetRadius * targetRadius - dist * dist);

		return lengthToClosestApproach - excessDistance;
	}

	/*
	 * Code used to find the highest priority target. If no sutable targets are
	 * found, null is returned. This method only returns a target if it can be
	 * fired at from the robot's current position.
	 */
	RobotInfo getHighestPriorityTarget(RobotInfo[] enemies) throws GameActionException {
		if (enemies.length == 0) {
			return null;
		}

		int maxIndex = -1;
		double maxPriority = -1;

		loop: for (int index = 0; index < enemies.length; index++) {

			double priority = 0;

			if (enemies[index].getType().canAttack()) {
				priority = enemies[index].getType().attackPower / Math.max(enemies[index].health, 1);
			}

			// TODO: Refactor
			if ((priority > maxPriority || (maxPriority == 0 && enemies[index].health < enemies[maxIndex].health))) {

				// Don't attack archons at the start of the game.
				if (enemies[index].type == RobotType.ARCHON && rc.getRoundNum() < ARCHON_IGNORE_ROUND) {
					continue loop;
				}

				Direction toEnemy = rc.getLocation().directionTo(enemies[index].location);
				float spawnOffset = rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET;

				MapLocation bulletSpawnPoint = rc.getLocation().add(toEnemy, spawnOffset);

				// Only attack if we will hit an enemy.
				if (getFirstHitTeam(bulletSpawnPoint, toEnemy) == getEnemyTeam()) {
					maxIndex = index;
					maxPriority = priority;
				}
			}
		}

		if (maxIndex >= 0) {
			return enemies[maxIndex];
		}
		// TODO: actually handle
		return null;
	}

	/*
	 * Returns the body that is closest to the robot tha calls the method. The
	 * passed in array should contain all bodies for analysis.
	 */
	protected BodyInfo getClosestBody(BodyInfo[] foes) {
		if (foes.length == 0) {
			return null;
		}

		BodyInfo closestEnemy = null;
		float closestDistance = Float.MAX_VALUE;

		loop: for (BodyInfo enemy : foes) {
			// Ignore enemy archons at the start of the game.
			if (enemy instanceof RobotInfo) {

				RobotInfo robot = (RobotInfo) enemy;
				if (robot.type == RobotType.ARCHON && rc.getRoundNum() < ARCHON_IGNORE_ROUND) {
					continue loop;
				}
			}

			float dist = rc.getLocation().distanceTo(enemy.getLocation());
			if (dist < closestDistance) {
				closestEnemy = enemy;
				closestDistance = dist;
			}
		}

		return closestEnemy;
	}

	protected void econWinIfPossible() throws GameActionException {
		if (rc.getTeamBullets() >= GameConstants.VICTORY_POINTS_TO_WIN * GameConstants.BULLET_EXCHANGE_RATE) {
			rc.donate(rc.getTeamBullets());
		}
	}

	private Direction findDensestDirection(BulletInfo[] bullets) {
		float avgX = 0, avgY = 0;
		MapLocation currLocation = rc.getLocation();

		for (BulletInfo bullet : bullets) {
			Direction d =  currLocation.directionTo(bullet.location);
			avgX += d.getDeltaX(1);
			avgY += d.getDeltaY(1);
		}

		avgX /= bullets.length;
		avgY /= bullets.length;

		return new Direction(avgX, avgY);
	}

	protected BulletInfo[] getAllIncomingBullets(BulletInfo[] bullets, MapLocation location, float angleTolerance) {
		ArrayList<BulletInfo> incoming = new ArrayList<>();
		for (BulletInfo bullet : bullets) {
			if (bullet.location.directionTo(location).degreesBetween(bullet.dir) < angleTolerance && bullet.location.distanceTo(location) < 5) {
				incoming.add(bullet);
			}
		}

		return incoming.toArray(new BulletInfo[incoming.size()]);
	}

	protected void dodge(BulletInfo[] bullets) throws GameActionException {
		BulletInfo[] predictNext = Arrays.stream(bullets)
				.map(b -> new BulletInfo(b.getID(), b.location.add(b.getDir(), b.speed),
						b.getDir(), b.getSpeed(), b.getDamage()))
				.toArray(BulletInfo[]::new);
		Direction densestDirection = findDensestDirection(bullets);
		MapLocation currentLocation = rc.getLocation();

		float stationaryImminentDanger = getImminenetDanger(bullets, currentLocation);

		int safestAngle = 0;
		float leastDanger = stationaryImminentDanger;
		for (int angle = 90; angle < 270; angle += 10) {

			float expectedDanger = getImminenetDanger(predictNext, currentLocation.add(densestDirection.rotateLeftDegrees(angle)));

			if (expectedDanger < leastDanger && rc.canMove(densestDirection.rotateLeftDegrees(angle))) {
				leastDanger = expectedDanger;
				safestAngle = angle;
			}
		}

		Direction toMove = densestDirection.rotateLeftDegrees(safestAngle);

		if (rc.canMove(toMove)) {
			rc.setIndicatorLine(currentLocation, currentLocation.add(densestDirection, 3), 255, 0, 0);
			rc.setIndicatorDot(currentLocation.add(densestDirection, 3), 0, 0, 255);
			rc.setIndicatorLine(currentLocation, currentLocation.add(toMove, 3), 0, 255, 0);
			rc.setIndicatorDot(currentLocation.add(toMove, 3), 0, 255, 0);
			rc.move(toMove);
		}

	}

	private float getImminenetDanger(BulletInfo[] bullets, MapLocation loc) {
		float danger = 0;
		for (BulletInfo bullet : bullets) {
			danger += bullet.getDamage() / loc.distanceTo(bullet.getLocation());
		}

		return danger + 2 * expectedDamage(bullets, loc);
	}

	/**
	 * This method returns the expected damage which a specific location
	 * will get in the next round, based on the current bullet information
	 * @param bullets
	 * @return
	 */
	protected float expectedDamage(BulletInfo[] bullets, MapLocation loc) {
		RobotInfo player = new RobotInfo(-1, null, rc.getType(), loc, 1, 1, 1);
		float totalDamage = 0;
		for (BulletInfo bullet : bullets) {
			if (willHit(bullet, player)) {
				totalDamage += bullet.damage;
			}
		}

		return totalDamage;
	}

	protected void dodge(BulletInfo toDodge) throws GameActionException {
		Direction toBullet = rc.getLocation().directionTo(toDodge.location);
		Direction toMove;
		for (int angle = 90; angle <= 180; angle += 10) {
			toMove = toBullet.rotateLeftDegrees(angle);
			if (rc.canMove(toMove)) {
				rc.move(toMove);
				return;
			}
			toMove = toBullet.rotateRightDegrees(angle);
			if (rc.canMove(toMove)) {
				rc.move(toMove);
				return;
			}
		}
	}

}
