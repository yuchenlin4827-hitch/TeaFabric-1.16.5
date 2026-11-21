tag @s add tf_parkour
give @s minecraft:diamond 3
tellraw @a [{"selector":"@s","color":"aqua"},{"text":" 爬上了跑酷塔！","color":"gold"}]
playsound minecraft:ui.toast.challenge_complete player @s
