package io.github.pinchan4273.reacademycraft.api;

/**
 * 発動の主体の種類（公開API、実験的）。自然能力者（{@link #NATURAL}）は技能のキーで発動する。
 * 装備・機械・AIの主体は、外部の発動（{@code AbilityActivation}）で使う（外部の主体は、外部の技能だけを発動できる）。
 */
public enum ActorKind { NATURAL, EQUIPMENT, MACHINE, AI }
