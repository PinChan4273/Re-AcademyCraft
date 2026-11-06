package io.github.pinchan4273.reacademycraft.develop;

/**
 * 開発機の種類。容量・刺激の間隔・刺激のコストは原作DeveloperType（WeAthFolD）、扱える技能のレベルの上限は
 * 原作Skill.getMinimumDeveloperType（レベル1〜2は携帯型、3は通常、4〜5は上位）のもの。
 * IFはForge Energyと区別して保つ（4 FE/IF）。
 */
public enum DeveloperTier {
    PORTABLE(2, 10000, 50, 25, 750),
    NORMAL(3, 50000, 100, 20, 700),
    ADVANCED(5, 200000, 300, 15, 600);

    public final int maximumSkillLevel, capacity, bandwidth, ticksPerStimulation, energyPerTick;
    DeveloperTier(int maximumSkillLevel, int capacity, int bandwidth, int ticks, int stimulationCost) {
        this.maximumSkillLevel = maximumSkillLevel; this.capacity = capacity; this.bandwidth = bandwidth;
        ticksPerStimulation = ticks + 1; // 原作DevelopDataは++tick > tpsを使う。
        energyPerTick = stimulationCost / ticks;
    }
    public boolean supportsSkill(int level) { return level >= 1 && level <= maximumSkillLevel; }
    public int cost(int stimulations) { return stimulations * ticksPerStimulation * energyPerTick; }
    public static int skillStimulations(int skillLevel) { return 3 + skillLevel * skillLevel / 2; }
    public static DeveloperTier fromId(int id) {
        return id >= 0 && id < values().length ? values()[id] : PORTABLE;
    }
}
