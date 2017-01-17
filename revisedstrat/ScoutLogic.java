package revisedstrat;

import battlecode.common.*;
import revisedstrat.BroadcastManager.LocationInfoType;
import revisedstrat.BroadcastManager.UnitCountInfoType;

/**
 * Created by patil215 on 1/12/17.
 */
public class ScoutLogic extends RobotLogic {
	public ScoutLogic(RobotController rc) {
		super(rc);
	}

	private MapLocation destination;

	@Override
	public void run() {
		boolean isDead = false;
		int birthRound = rc.getRoundNum();
		while (true) {
			try {
				System.out.println(rc.getRoundNum() - birthRound);
				if (rc.getRoundNum() - birthRound == 20) {
					BroadcastManager.incrementUnitCount(rc, UnitCountInfoType.ALLY_SCOUT);
					System.out.println("Reported being alive");
				}
				if (rc.getHealth() < RobotType.SCOUT.maxHealth / 5 && rc.getRoundNum() - birthRound >= 20 && !isDead) {
					BroadcastManager.decrementUnitCount(rc, UnitCountInfoType.ALLY_SCOUT);
					isDead = true;
				}
				// Check if we have company
				RobotInfo[] foes = rc.senseNearbyRobots(-1, getEnemyTeam());
				boolean foundEconUnit = false;
				for (RobotInfo info : foes) {
					if (info.type == RobotType.GARDENER || (info.type == RobotType.ARCHON && rc.getRoundNum() > 200)) {
						foundEconUnit = true;
					}
				}
				if (foundEconUnit) {
					// Otherwise, we can harass the enemy.
					handleHarass(foes);
				} else if (foes.length > 0) {
					handleAttack(foes);
				}

				// There are no enemies in sight
				else {
					handleRecon();
				}

				tryAndShakeATree();
				econWinIfPossible();
			} catch (Exception e) {
				e.printStackTrace();
			}

			Clock.yield();
		}
	}

	// TODO: First handle broadcasted information. Also, find something to do if
	// initial archon locations are abandoned.
	private void handleRecon() throws GameActionException {
		MapLocation recentEnemyLoc = BroadcastManager.getRecentLocation(rc, LocationInfoType.ENEMY);
		if (recentEnemyLoc != null && closeToLocationAndNoEnemies(recentEnemyLoc)) {
			BroadcastManager.invalidateLocation(rc, LocationInfoType.ENEMY);
			destination = null;
		}

		if (destination == null) {
			MapLocation recentEnemyLocation = BroadcastManager.getRecentLocation(rc, LocationInfoType.ENEMY);
			if (recentEnemyLocation != null) {
				destination = recentEnemyLocation;
			} else {
				MapLocation[] broadcastLocations = rc.senseBroadcastingRobotLocations();
				if (broadcastLocations.length != 0) {
					int broadcastIndex = (int) (Math.random() * broadcastLocations.length);
					destination = broadcastLocations[broadcastIndex];
				} else {
					Direction move = moveTowards(getRandomEnemyInitialArchonLocation());
					if (move != null) {
						move(move);
					}
				}
			}
		}

		if (destination != null) {
			Direction toMove = moveTowards(destination);
			if (toMove != null) {
				move(toMove);
			}
			if (rc.canSenseLocation(destination) && rc.senseNearbyRobots(-1, getEnemyTeam()).length == 0) {
				destination = null;
			}
		}
	}

	// TODO: Actually allow shooting at a distance against archons. Use attack
	// code for this?
	private void handleHarass(RobotInfo[] foes) throws GameActionException {
		RobotInfo target = getPriorityEconTarget(foes);
		if (target != null) {
			int bytecode = Clock.getBytecodeNum();
			BroadcastManager.saveLocation(rc, target.location, LocationInfoType.ENEMY);
			System.out.println("Broadcasting: " + (Clock.getBytecodeNum() - bytecode));

			BulletInfo[] bullets = rc.senseNearbyBullets();

			bytecode = Clock.getBytecodeNum();
			BulletInfo toDodge = getTargetingBullet(bullets);
			System.out.println("Finding bullet: " + (Clock.getBytecodeNum() - bytecode));

			if (toDodge != null) {
				bytecode = Clock.getBytecodeNum();
				dodge(bullets);
				System.out.println("Dodging: " + (Clock.getBytecodeNum() - bytecode));
			} else {
				Direction toMove = moveTowards(target.location);
				if (toMove != null) {
					move(toMove);
				}
			}

			bytecode = Clock.getBytecodeNum();
			RobotInfo potentialTarget = getHighestPriorityTarget(rc.senseNearbyRobots(-1, getEnemyTeam()));
			System.out.println("Prioritizing: " + (Clock.getBytecodeNum() - bytecode));
			if (potentialTarget != null && rc.canFireSingleShot()) {
				rc.fireSingleShot(rc.getLocation().directionTo(potentialTarget.location));
			}
		}
		// TODO: refactor?
		else {
			handleRecon();
		}
	}

	private void handleAttack(RobotInfo[] foes) throws GameActionException {
		BulletInfo[] bullets = rc.senseNearbyBullets(5);
		BulletInfo toDodge = getTargetingBullet(bullets);
		RobotInfo threat = (RobotInfo) getClosestBody(foes);
		if (toDodge != null) {
			dodge(bullets);
		} else {
			if (rc.getLocation().distanceTo(threat.location) <= 7) {
				// We are too close to the enemy. They can see us.
				Direction toMove = moveTowards(rc.getLocation().directionTo(threat.location).opposite());
				if (toMove != null) {
					move(toMove);
				}
			}
		}
		RobotInfo target = getHighestPriorityTarget(foes);
		if (target != null && rc.canFireSingleShot()) {
			rc.fireSingleShot(rc.getLocation().directionTo(target.location));
		}
		if (!rc.hasAttacked() && !rc.hasMoved()) {
			Direction towardsThreat = rc.getLocation().directionTo(threat.location);
			Direction toMove = moveTowards(towardsThreat.rotateLeftDegrees(90));
			if (toMove != null && rc.canMove(toMove)) {
				move(toMove);
			} else {
				toMove = moveTowards(towardsThreat.rotateRightDegrees(90));
				if (toMove != null && rc.canMove(toMove)) {
					move(toMove);
				}
			}
		}
	}

	private RobotInfo getPriorityEconTarget(RobotInfo[] foes) {
		RobotInfo target = null;
		float leastDistance = Float.MAX_VALUE;
		for (RobotInfo info : foes) {
			if (info.type == RobotType.GARDENER || (rc.getType() == RobotType.ARCHON && rc.getRoundNum() > 200)) {
				float distance = rc.getLocation().distanceTo(info.location);
				if (distance < leastDistance) {
					leastDistance = distance;
					target = info;
				}
			}
		}
		return target;
	}

	/**
	 * This method dodges a list of bullets, and returns an integer status code.
	 * <p>
	 * 0 - bullets were successfully dodged -1 - no call to move() was made, and
	 * dodging either failed, or was unnecessary.
	 *
	 * @param bullets
	 * @return
	 * @throws GameActionException
	 */
	private int dodgeDefaultBullets(BulletInfo[] bullets) throws GameActionException {
		MapLocation currentLocation = rc.getLocation();

		BulletInfo[] dangerousBullets = getAllTargetingBullets(bullets, currentLocation);

		if (dangerousBullets.length > 5) {

		}

		if (dangerousBullets.length == 0) {
			System.out.println("NO BULLETS TO DODGE");
			return -1;
		}

		BulletInfo toDodge = dangerousBullets[0];
		Direction toFirstBullet = rc.getLocation().directionTo(toDodge.location);
		Direction toMove;
		int leastDangerous = 90;
		int danger = Integer.MAX_VALUE;
		for (int angle = 90; angle <= 270; angle += 10) {
			toMove = toFirstBullet.rotateLeftDegrees(angle);

			int currDanger = getAllTargetingBullets(bullets, currentLocation.add(toMove, (float) 2.5)).length;
			if (currDanger < danger && rc.canMove(toMove)) {
				danger = currDanger;
				leastDangerous = angle;
			}

			// if (rc.canMove(toMove)) {
			// move(toMove);
			// return 0;
			// }

			// toMove = toFirstBullet.rotateRightDegrees(angle);
			// if (rc.canMove(toMove)) {
			// move(toMove);
			// return 0;
			// }
			// currDanger = getAllTargetingBullets(bullets,
			// currentLocation.add(toMove, (float) 2.5)).length;
			// if (currDanger < danger && rc.canMove(toMove)) {
			// danger = currDanger;
			// leastDangerous = angle;
			// }
		}

		System.out.println("DANGER: " + danger);

		if (danger != Integer.MAX_VALUE)
			move(toFirstBullet.rotateLeftDegrees(leastDangerous));

		return 0;
	}

	private RobotInfo getLowestHealthEconTarget(RobotInfo[] foes) {
		if (foes.length == 0) {
			return null;
		}

		RobotInfo lowestHealthEnemy = foes[0];
		for (RobotInfo enemy : foes) {
			if (enemy.health < lowestHealthEnemy.health) {
				lowestHealthEnemy = enemy;
			}
		}

		return lowestHealthEnemy;
	}
}
