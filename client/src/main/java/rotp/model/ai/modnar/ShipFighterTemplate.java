/*
 * Copyright 2015-2020 Ray Fowler
 * 
 * Licensed under the GNU General Public License, Version 3 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     https://www.gnu.org/licenses/gpl-3.0.html
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rotp.model.ai.modnar;

import java.util.ArrayList;
import java.util.List;
import rotp.model.ai.EnemyShipTarget;
import rotp.model.ai.interfaces.ShipDesigner;
import rotp.model.empires.Empire;
import rotp.model.empires.EmpireView;
import rotp.model.ships.ShipArmor;
import rotp.model.ships.ShipComputer;
import rotp.model.ships.ShipDesign;
import rotp.model.ships.ShipECM;
import rotp.model.ships.ShipManeuver;
import rotp.model.ships.ShipShield;
import rotp.model.ships.ShipSpecial;
import rotp.model.ships.ShipWeapon;
import rotp.util.Base;

public class ShipFighterTemplate implements Base {
    private static final List<DesignDamageSpec> dmgSpecs = new ArrayList<>();
    private static final ShipFighterTemplate instance = new ShipFighterTemplate();
    private static final ShipDesign mockDesign = new ShipDesign();

    public static ShipDesign newDesign(ShipDesigner ai) {
        return instance.bestDesign(ai);
    }
    public static void setPerTurnDamage(ShipDesign d, Empire emp) {
        List<EnemyShipTarget> targets = buildTargetList(emp);
        float minDamage = Float.MAX_VALUE;
        for (EnemyShipTarget tgt: targets) {
            float targetDmg = estimatedDamage(d, tgt);
            minDamage = Math.min(minDamage, targetDmg);
        }
        d.perTurnDamage(minDamage);
    }
    public static float estimatedDamage(ShipDesign d, EnemyShipTarget target) {
        List<ShipSpecial> rangeSpecials = new ArrayList<>();
        for (int i=0;i<ShipDesign.maxSpecials();i++) {
            ShipSpecial sp = d.special(i);
            if (sp.allowsCloaking()
            || (sp.allowsTeleporting() && !target.hasInterdictors)
            || (sp.beamRangeBonus() >= 2))
                rangeSpecials.add(sp);
        }
        float totalDamage = 0;
        for (int i=0;i<ShipDesign.maxWeapons();i++) {
            float wpnDamage = 0;
            ShipWeapon wpn = d.weapon(i);
            if (wpn.noWeapon() || wpn.groundAttacksOnly())
                wpnDamage = 0;
            else if (target.hasRepulsors && (wpn.range() < 2) && rangeSpecials.isEmpty())
                wpnDamage = 0;
            else {
                wpnDamage = d.wpnCount(i) * wpn.firepower(target.shieldLevel);
                if (wpn.isLimitedShotWeapon())
                        wpnDamage = wpnDamage * wpn.shots() / 10;
                // divide by # of turns to fire
                wpnDamage /= wpn.turnsToFire();
                // +15% damage for each weapon computer level
                // this estimates increased dmg from +hit
                wpnDamage *= (1+ (.15f * wpn.computerLevel()));
            }
            totalDamage += wpnDamage;
        }
        return totalDamage;
    }
    private ShipDesign bestDesign(ShipDesigner ai) {
        List<EnemyShipTarget> targets = buildTargetList(ai.empire());
        int preferredSize = ai.optimalShipFighterSize();
        ShipDesign des = newDesign(ai, preferredSize, targets);
        while (ineffective(des) && (des.size() < ShipDesign.LARGE))
            des = newDesign(ai, des.size() + 1, targets);
        
        return des;
    }
    private ShipDesign newDesign(ShipDesigner ai, int size, List<EnemyShipTarget> targets) {
        ShipDesign d = ai.lab().newBlankDesign(size);
        setFastestEngine(ai, d);
        float totalSpace = d.availableSpace();
        setBestCombatSpeed(ai, d);
        if (d.size() >= ShipDesign.SMALL) {
            setBestNormalArmor(ai, d);  // normal armor for all sizes
            set2ndBestBattleComputer(ai, d); // set 2nd best battle computer for smaller ships
        }
        
        if (d.size() == ShipDesign.MEDIUM) {
            set2ndBestShield(ai, d); // give 2nd best shield for MEDIUM
        }
        
        if (d.size() >= ShipDesign.LARGE) {
            setBestBattleComputer(ai, d);
            setBattleScanner(ai, d);
            setBestShield(ai, d);
            set2ndBestECMJammer(ai, d); // give 2nd best ECM to LARGE and HUGE
        }

        float weaponSpace = d.availableSpace();
        
        // if ship is medium or small and more than 50% of space is already going
        // to computer & manv, then quit and try a larger hull size
        if ((d.size() < ShipDesign.LARGE) && (weaponSpace < (totalSpace/2))) {
            d.perTurnDamage(0);
            return d;
        }
        
        setOptimalShipCombatWeapon(ai, d, targets);
        upgradeShipManeuverSpecial(ai, d, targets);
        upgradeBeamRangeSpecial(ai, d);

        ai.lab().nameDesign(d);
        ai.lab().iconifyDesign(d);
        return d;
    }
    private void setFastestEngine(ShipDesigner ai, ShipDesign d) {
        d.engine(ai.lab().fastestEngine());
    }
    private void setBestCombatSpeed(ShipDesigner ai, ShipDesign d) {
        for (ShipManeuver manv : ai.lab().maneuvers()) {
            ShipManeuver prevManv = d.maneuver();
            int prevSpeed = d.combatSpeed();
            float prevSpace = d.availableSpace();
            d.maneuver(manv);
            if ((d.combatSpeed() == prevSpeed) && (d.availableSpace() < prevSpace))
                d.maneuver(prevManv);
        }
    }
    private void setBattleScanner(ShipDesigner ai, ShipDesign d) {
        int nextSlot = d.nextEmptySpecialSlot();
        if (nextSlot < 0)
            return;
        ShipSpecial scanner = ai.lab().specialBattleScanner();
        if (scanner == null)
            return;

        ShipSpecial old = d.special(nextSlot);
        d.special(nextSlot, scanner);
        if (d.availableSpace() < 0)
            d.special(nextSlot, old);
    }
    private void setBestBattleComputer(ShipDesigner ai, ShipDesign d) {
        List<ShipComputer> comps = ai.lab().computers();
        for (int i=comps.size()-1; i >=0; i--) {
            d.computer(comps.get(i));
            if (d.availableSpace() >= 0)
                return;
        }
    }
 // add 2nd best battle computer option
    private void set2ndBestBattleComputer(ShipDesigner ai, ShipDesign d) {
        List<ShipComputer> comps = ai.lab().computers();
        for (int i=comps.size()-2; i >=0; i--) {
            d.computer(comps.get(i));
            if (d.availableSpace() >= 0)
                return;
        }
    }
    private void setBestECMJammer(ShipDesigner ai, ShipDesign d) {
        List<ShipECM> comps = ai.lab().ecms();
        for (int i=comps.size()-1; i >=0; i--) {
            d.ecm(comps.get(i));
            if (d.availableSpace() >= 0)
                return;
        }
    }
 // add 2nd best ECM option
    private void set2ndBestECMJammer(ShipDesigner ai, ShipDesign d) {
        List<ShipECM> comps = ai.lab().ecms();
        for (int i=comps.size()-2; i >=0; i--) {
            d.ecm(comps.get(i));
            if (d.availableSpace() >= 0)
                return;
        }
    }
    private void setBestNormalArmor(ShipDesigner ai, ShipDesign d) {
        List<ShipArmor> armors = ai.lab().armors();
        for (int i=armors.size()-1; i >=0; i--) {
            ShipArmor arm = armors.get(i);
            if (!arm.reinforced()) {
                d.armor(armors.get(i));
                if (d.availableSpace() >= 0)
                    return;
            }
        }
    }
    private void setBestShield(ShipDesigner ai, ShipDesign d) {
        List<ShipShield> shields = ai.lab().shields();
        for (int i=shields.size()-1; i >=0; i--) {
            d.shield(shields.get(i));
            if (d.availableSpace() >= 0)
                return;
        }
    }
     // add 2nd best shield option
    private void set2ndBestShield(ShipDesigner ai, ShipDesign d) {
        List<ShipShield> shields = ai.lab().shields();
        for (int i=shields.size()-2; i >=0; i--) {
            d.shield(shields.get(i));
            if (d.availableSpace() >= 0)
                return;
        }
    }
    public static List<EnemyShipTarget> buildTargetList(Empire emp) {
        List<EnemyShipTarget> targets = new ArrayList<>();

        // build list from hostile empires (non-pact, non-ally)
        for (EmpireView ev : emp.hostiles())
            targets.add(new EnemyShipTarget(ev.spies().tech()));

        // if none, build list from all empires
        if (targets.isEmpty()) {
            for (EmpireView ev : emp.empireViews()) {
                if (ev != null)
                    targets.add(new EnemyShipTarget(ev.spies().tech()));
            }
        }

        // if no contacted empires, use ourselves as template
        if (targets.isEmpty())
            targets.add(new EnemyShipTarget(emp.tech()));

        return targets;
    }
    private void setOptimalShipCombatWeapon(ShipDesigner ai, ShipDesign d, List<EnemyShipTarget> targets) {
        List<ShipWeapon> allWeapons = ai.lab().weapons();
        List<ShipSpecial> allSpecials = ai.lab().specials();

        List<ShipSpecial> rangeSpecials = new ArrayList<>();
        for (ShipSpecial sp: allSpecials) {
            if (sp.allowsCloaking()
            ||  sp.allowsTeleporting()
            || (sp.beamRangeBonus() >= 2))
                rangeSpecials.add(sp);
        }

        DesignDamageSpec maxDmgSpec = newDamageSpec();
        for (ShipWeapon wpn: allWeapons) {
            if (wpn.canAttackShips()) {
                DesignDamageSpec minDmgSpec = newDamageSpec();
                minDmgSpec.damage = Float.MAX_VALUE;
                for (EnemyShipTarget tgt: targets) {
                    DesignDamageSpec spec = simulateDamage(d, wpn, rangeSpecials, tgt);
                    if (minDmgSpec.damage > spec.damage)
                        minDmgSpec.set(spec);
                }
                if (maxDmgSpec.damage < minDmgSpec.damage)
                    maxDmgSpec.set(minDmgSpec);
            }
        }

        // at this point, maxDmgSpec is the optimum
        // spread out the weapon count across all 4 weapon slots
        // using (int) Math.ceil((float)num/(maxSlots-slot)) ensures
        // equal distribution with highest first.. i.e 22 = 6 6 5 5
        if (maxDmgSpec.weapon != null) {
            int num = maxDmgSpec.numWeapons;
            int maxSlots = ShipDesign.maxWeapons;
            for (int slot=0;slot<maxSlots;slot++) {
                int numSlot = (int) Math.ceil((float)num/(maxSlots-slot));
                if (numSlot > 0) {
                    d.weapon(slot, maxDmgSpec.weapon);
                    d.wpnCount(slot, numSlot);
                    num -= numSlot;
                }
            }
        }
        if (maxDmgSpec.special != null) {
            int spSlot = d.nextEmptySpecialSlot();
            d.special(spSlot, maxDmgSpec.special);
        }
        d.perTurnDamage(maxDmgSpec.damage);
        maxDmgSpec.reclaim();
    }
    private DesignDamageSpec simulateDamage(ShipDesign d, ShipWeapon wpn, List<ShipSpecial> specials, EnemyShipTarget target) {
        DesignDamageSpec spec = newDamageSpec();
        spec.weapon = wpn;
        spec.damage = 0;

        mockDesign.copyFrom(d);
        int wpnSlot = mockDesign.nextEmptyWeaponSlot();
        int specSlot = mockDesign.nextEmptySpecialSlot();
        int numWeapons = (int) (d.availableSpace()/wpn.space(d));

        mockDesign.wpnCount(wpnSlot, numWeapons);
        mockDesign.weapon(wpnSlot, wpn);

        float wpnDamage = estimatedDamage(mockDesign, target);
        if (wpnDamage > spec.damage) {
            spec.special = null;
            spec.weapon = wpn;
            spec.numWeapons = numWeapons;
            spec.damage = wpnDamage;
        }

        for (ShipSpecial sp: specials) {
            mockDesign.special(specSlot, sp);
            wpnDamage = estimatedDamage(mockDesign, target);
            if (wpnDamage > spec.damage) {
                spec.special = sp;
                spec.weapon = wpn;
                spec.numWeapons = numWeapons;
                spec.damage = wpnDamage;
            }
        }
        return spec;
    }
    private void upgradeShipManeuverSpecial(ShipDesigner ai, ShipDesign d, List<EnemyShipTarget> targets) {
        // if we already have added teleporters, we can skip
        if (d.allowsTeleporting())
            return;

        // if we don't have room for more specials, we can skip
        int slot1 = d.nextEmptySpecialSlot();
        if (slot1 < 0)
            return;

        float wpnCombatSpeedFactor = 0.70f;
        int maxSpeed = 9;

        boolean hasInterdictors = false;
        for (EnemyShipTarget tgt: targets)
                hasInterdictors = hasInterdictors || tgt.hasInterdictors;
        if (!hasInterdictors) {
            ShipSpecial spec = ai.lab().specialTeleporter();
            if (spec != null) {
                int speedDiff = maxSpeed - d.combatSpeed();
                int wpnCount = d.wpnCount(0);
                int minNewWpnCount = (int) Math.ceil(wpnCount*Math.pow(wpnCombatSpeedFactor, speedDiff));
                // calc reduction in space and how many weapons need to be removed
                float spaceLost = d.availableSpace() + d.special(slot1).space(d) - spec.space(d);
                int wpnRemoved = (int) Math.floor(spaceLost/ d.weapon(0).space(d));
                int newWpnCount = wpnCount+wpnRemoved;
                if (newWpnCount >= minNewWpnCount) {
                    d.special(slot1,spec);
                    d.wpnCount(0,newWpnCount);
                }
            }
        }

        // if we added teleporters, done
        if (d.allowsTeleporting())
            return;

        // go through specials that improve compat speed (inertials)
        int baseSpeed = d.combatSpeed();

        List<ShipSpecial> specials = ai.lab().specials();
        for (ShipSpecial spec: specials) {
            int speedBonus = spec.speedBonus();
            if (speedBonus > 0) {
                int newSpeed = Math.min(maxSpeed, baseSpeed+speedBonus);
                int speedDiff = newSpeed - d.combatSpeed();
                int wpnCount = d.wpnCount(0);
                int minNewWpnCount = (int) Math.ceil(wpnCount*Math.pow(wpnCombatSpeedFactor, speedDiff));
                // calc reduction in space and how many weapons need to be removed
                float spaceLost = d.availableSpace() + d.special(slot1).space(d) - spec.space(d);
                int wpnRemoved = (int) Math.floor(spaceLost/ d.weapon(0).space(d));
                int newWpnCount = wpnCount+wpnRemoved;
                if (newWpnCount >= minNewWpnCount) {
                    d.special(slot1,spec);
                    d.wpnCount(0,newWpnCount);
                }
            }
        }
    }
    private void upgradeBeamRangeSpecial(ShipDesigner ai, ShipDesign d) {
        // if not using a beam weapon, then skip
        if (!d.weapon(0).isBeamWeapon())
            return;
        // if teleporters or max combat speed, skip
        if (d.allowsTeleporting() || (d.combatSpeed() >= 9))
            return;
        // if we don't have room for more specials, we can skip
        int slot1 = d.nextEmptySpecialSlot();
        if (slot1 < 0)
            return;

        // go through specials that improve compat speed (inertials)
        int addlRange = 0;
        float wpnRangeFactor = 0.95f;

        List<ShipSpecial> specials = ai.lab().specials();
        for (ShipSpecial spec: specials) {
            int rangeBonus = spec.beamRangeBonus();
            if (rangeBonus > 0) {
                int rangeDiff = rangeBonus - addlRange;
                int wpnCount = d.wpnCount(0);
                int minNewWpnCount = (int) Math.ceil(wpnCount*Math.pow(wpnRangeFactor, rangeDiff));
                // calc reduction in space and how many weapons need to be removed
                float spaceLost = d.availableSpace() + d.special(slot1).space(d) - spec.space(d);
                int wpnRemoved = (int) Math.floor(spaceLost/ d.weapon(0).space(d));
                int newWpnCount = wpnCount+wpnRemoved;
                if (newWpnCount >= minNewWpnCount) {
                    addlRange = rangeBonus;
                    d.special(slot1,spec);
                    d.wpnCount(0,newWpnCount);
                }
            }
        }
    }
    private DesignDamageSpec newDamageSpec() {
        if (dmgSpecs.isEmpty())
            return new DesignDamageSpec();
        else
            return dmgSpecs.remove(0);
    }
    private boolean ineffective(ShipDesign d) {
        return d.perTurnDamage() == 0;
    }
    class DesignDamageSpec {
        public int numWeapons = 0;
        public ShipWeapon weapon;
        public ShipSpecial special;
        public float damage;
        public void set(DesignDamageSpec spec) {
            numWeapons = spec.numWeapons;
            weapon = spec.weapon;
            special = spec.special;
            damage = spec.damage;
            spec.reclaim();
        }
        public void reclaim() {
            numWeapons = 0;
            weapon = null;
            special = null;
            damage = 0;
            dmgSpecs.add(this);
        }
    }
}
