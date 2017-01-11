package scoutrushtwo;

import java.util.Arrays;

import battlecode.common.BulletInfo;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;
import battlecode.common.TreeInfo;

public strictfp class RobotPlayer {
	static RobotController rc;

	/**
	 * run() is the method that is called when a robot is instantiated in the
	 * Battlecode world. If this method returns, the robot dies!
	 **/
	@SuppressWarnings("unused")
	public static void run(RobotController rc) throws GameActionException {

		// This is the RobotController object. You use it to perform actions
		// from this robot,
		// and to get information on its current status.
		RobotPlayer.rc = rc;

		// Here, we've separated the controls into a different method for each
		// RobotType.
		// You can add the missing ones or rewrite this into your own control
		// structure.
		switch (rc.getType()) {
		case ARCHON:
			runArchon();
			break;
		case GARDENER:
			runGardener();
			break;
		case SCOUT:
			runScout();
			break;
		}
	}

	private static void runScout() throws GameActionException {
		Direction move = randomDirection();
		Team enemy = rc.getTeam().opponent();
		MapLocation destination = null;
		boolean reconMode = true;
		while (true) {
			try {
				if (rc.senseNearbyBullets().length > 0 || rc.senseNearbyRobots(-1, enemy).length > 0) {
					reconMode = false;
				} else {
					reconMode = true;
				}

				if (reconMode) {
					if (rc.readBroadcast(0) != -1) {
						destination = new MapLocation(rc.readBroadcast(0), rc.readBroadcast(1));
					}
					if (destination == null) {
						if (rc.canMove(move)) {
							rc.move(move);
						} else {
							for (int count = 0; count < 100 && !rc.canMove(move); count++) {
								move = randomDirection();
							}
							if (rc.canMove(move)) {
								rc.move(move);
							}
						}
					} else {
						if (rc.canSenseLocation(destination)) {
							destination = null;
							if(rc.readBroadcast(0)==destination.x&&rc.readBroadcast(1)==destination.y){
								rc.broadcast(0, -1);
								rc.broadcast(1, -1);
							}
						} else {
							move = moveTowards(destination);
							if (rc.canMove(move)) {
								rc.move(move);
							}
						}
					}
				}

				else {
					BulletInfo[] bullets = rc.senseNearbyBullets();
					if (bullets.length > 0) {
						int minAngleCount = Integer.MAX_VALUE;
						Direction minAngle = new Direction(0);
						for (float moveAngle = 0; moveAngle < 2 * Math.PI; moveAngle += Math.PI / 6) {
							Direction testDirection = new Direction(moveAngle);
							if (rc.canMove(testDirection, (float) 2.5)) {
								int angleCount = 0;
								for (int index = 0; index < bullets.length; index++) {
									if (RobotPlayer.willBeHitByBullet(bullets[index],
											rc.getLocation().add(testDirection, (float) 2.5))) {
										angleCount++;
									}
								}
								if (angleCount < minAngleCount) {
									minAngleCount = angleCount;
									minAngle = testDirection;
								}
							}
						}
						rc.move(minAngle);
					}
					RobotInfo[] foes = rc.senseNearbyRobots(-1, enemy);
					boolean foundEconUnit = false;
					if (foes.length > 0) {
						for (int index = 0; index < foes.length && !foundEconUnit; index++) {
							if (foes[index].type == RobotType.GARDENER || foes[index].type == RobotType.ARCHON) {
								foundEconUnit = true;
								Direction toEcon = moveTowards(foes[index].location);
								rc.broadcast(0, (int) foes[index].location.x);
								rc.broadcast(0, (int) foes[index].location.y);
								if (!rc.hasMoved() && rc.canMove(toEcon)) {
									rc.move(toEcon, (float) 1.25);
								}
								if (RobotPlayer.fireBulletImpact(rc.getLocation().directionTo(foes[index].location),
										rc.getLocation(), (float) 1.5) > 0) {
									rc.fireSingleShot(rc.getLocation().directionTo(foes[index].location));
								} else {
									RobotInfo target = getHighestPriorityTarget(foes);
									if (target != null) {
										rc.fireSingleShot(rc.getLocation().directionTo(target.location));
									}
								}
							}
						}
						if (!foundEconUnit) {
							RobotInfo nearestEnemy = findNearestEnemy();
							if (nearestEnemy != null) {
								Direction toEnemy = rc.getLocation().directionTo(nearestEnemy.location);
								Direction fromEnemy = moveTowards(toEnemy.opposite());
								if (rc.canMove(fromEnemy)) {
									rc.move(fromEnemy);
								}
								if (rc.canFireSingleShot()
										&& RobotPlayer.fireBulletImpact(toEnemy, rc.getLocation(), (float) 1.5) > 0) {
									rc.fireSingleShot(toEnemy);
								}
							}
						}
					}
				}
				if (!rc.hasMoved()) {
					move = randomDirection();
					if (rc.canMove(move)) {
						rc.move(move);
					}
				}
				Clock.yield();
			} catch (Exception e) {
				System.out.println(e);
				e.printStackTrace(System.out);
			}
		}
	}

	private static Direction moveTowards(Direction move) {
		if (rc.canMove(move)) {
			return move;
		} else {
			boolean foundMoveAngle = false;
			for (float deltaAngle = 0; deltaAngle < 180 && !foundMoveAngle; deltaAngle += 45) {
				if (rc.canMove(move.rotateRightDegrees(deltaAngle))) {
					move = move.rotateRightDegrees(deltaAngle);
					foundMoveAngle = true;
				} else if(rc.canMove(move.rotateLeftDegrees(deltaAngle))){
					move = move.rotateLeftDegrees(deltaAngle);
					foundMoveAngle = true;
				}
			}
		}
		return move;
	}

	private static RobotInfo findNearestEnemy() {
		RobotInfo[] closeRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
		float nearestDistance = Float.MAX_VALUE;
		RobotInfo nearestEnemy = null;
		for (int index = 0; index < closeRobots.length; index++) {
			float newDistance = rc.getLocation().distanceTo(closeRobots[index].location);
			if (newDistance < nearestDistance && nearestEnemy.type!=RobotType.ARCHON && nearestEnemy.type!=RobotType.ARCHON.GARDENER) {
				nearestDistance = newDistance;
				nearestEnemy = closeRobots[index];
			}
		}
		return nearestEnemy;
	}

	private static Direction moveTowards(MapLocation destination) throws GameActionException {
		Direction move;
		move = rc.getLocation().directionTo(destination);
		return moveTowards(move);
	}

	private static void runGardener() throws GameActionException {
		Direction move = randomDirection();
		int treeCount = 3;
		int roundsToSettle = (int) (Math.random() * 20);
		try {
			while (true) {
				if (roundsToSettle > 0 && rc.canMove(move)) {
					rc.move(move);
					roundsToSettle--;
				} else if (roundsToSettle > 0) {
					move = randomDirection();
				} else {
					if (treeCount > 0) {
						if (rc.canPlantTree(move)) {
							rc.plantTree(move);
							treeCount--;
						} else {
							move = move.rotateLeftDegrees((float) (Math.PI / 5));
						}
					} else {
						if (rc.canBuildRobot(RobotType.SCOUT, move)) {
							rc.buildRobot(RobotType.SCOUT, move);
						} else {
							move = move.rotateLeftDegrees((float) (Math.PI / 5));
						}
					}

					TreeInfo[] trees = rc.senseNearbyTrees();

					if (trees.length > 0) {
						int minHealthID = -1;
						float minHealth = Float.MAX_VALUE;
						for (int count = 0; count < trees.length; count++) {
							if (rc.canWater(trees[count].ID) && trees[count].health < minHealth) {
								minHealth = trees[count].health;
								minHealthID = trees[count].ID;
							}
						}
						if (rc.canWater(minHealthID)) {
							rc.water(minHealthID);
						}
					}
				}

				if (rc.getTeamBullets() >= 10000) {
					rc.donate(10000);
				}
				Clock.yield();
			}
		} catch (Exception e) {
			System.out.println(e);
		}

	}

	private static void runArchon() throws GameActionException {
		rc.broadcast(0, -1);
		rc.broadcast(1, -1);
		int numGardeners = 7;
		Direction move = randomDirection();
		while (true) {
			if (rc.canMove(move)) {
				rc.move(move);
			} else {
				move = randomDirection();
			}
			if (rc.canHireGardener(move.opposite()) && numGardeners > 0) {
				rc.hireGardener(move.opposite());
				numGardeners--;
			}
			if (rc.getTeamBullets() >= 10000) {
				rc.donate(10000);
			}
			Clock.yield();
		}

	}

	// 0 means that the bullet will not hit anything in your line of sight.
	// 1 means that the bullet will hit an opponent
	// -1 means that the bullet will hit an ally
	// The bullet ignores hitting you, as this method should be used to
	// determine if a bullet should be fired at a given angle.
	// Generally, you should only fire a bullet if a 1 is returned.
	private static int fireBulletImpact(Direction dir, MapLocation location, float speed) throws GameActionException {
		while (rc.canSenseLocation(location)) {
			if (rc.isLocationOccupied(location)) {
				if (rc.isLocationOccupiedByTree(location)) {
					return 0;
				} else if (rc.isLocationOccupiedByRobot(location)) {
					if (rc.senseRobotAtLocation(location).team == rc.getTeam().opponent()) {
						return 1;
					} else if (rc.senseRobotAtLocation(location).ID != rc.getID()) {
						return -1;
					}
				}
			}
			location = location.add(dir, speed);
		}
		return 0;
	}

	// Returns true if the bot will be hit by the bullet if it is at
	// playerLocation.
	static boolean willBeHitByBullet(BulletInfo info, MapLocation playerLocation) throws GameActionException {
		MapLocation location = info.location;
		while (rc.canSenseLocation(location)) {
			if (rc.isLocationOccupiedByTree(location)) {
				return false;
			} else if (rc.isLocationOccupiedByRobot(location)) {
				if (rc.senseRobotAtLocation(location).ID == rc.getID()) {
					return true;
				}
				return false;
			}
			location = location.add(info.dir, info.speed);
		}
		return false;
	}

	static Direction randomDirection() {
		return new Direction((float) Math.random() * 2 * (float) Math.PI);
	}

	static RobotInfo getHighestPriorityTarget(RobotInfo[] enemies) throws GameActionException {
		if (enemies.length == 0) {
			return null;
		}

		int maxIndex = -1;
		double maxPriority = -1;

		for (int index = 0; index < enemies.length; index++) {
			double priority = enemies[index].getType().attackPower / enemies[index].health;

			if (priority > maxPriority
					&& RobotPlayer.fireBulletImpact(rc.getLocation().directionTo(enemies[index].location),
							rc.getLocation(), (float) 1.5) > 0) {
				maxIndex = index;
				maxPriority = priority;
			}
		}

		if (maxIndex >= 0) {
			return enemies[maxIndex];
		}
		return null;
	}
}
