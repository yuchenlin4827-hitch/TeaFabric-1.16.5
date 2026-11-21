execute if entity @s[tag=!tf_parkour] run function tf:parkour_reward
execute if entity @s[tag=tf_parkour_seen] run title @s actionbar {"text":"獎勵每人只有一次","color":"gray"}
tag @s add tf_parkour_seen
tp @s 227.5 63 80.5 180 0
