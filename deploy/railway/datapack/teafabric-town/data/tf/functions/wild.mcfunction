spreadplayers 244 56 0 2000 false @s
effect give @s minecraft:resistance 10 4 true
tellraw @s [{"text":"已傳送到荒野。回出生點：","color":"green"},{"text":"/trigger spawn","color":"yellow"}]
playsound minecraft:entity.enderman.teleport player @s
