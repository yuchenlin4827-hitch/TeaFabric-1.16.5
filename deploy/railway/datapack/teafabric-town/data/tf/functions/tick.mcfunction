# Every tick
scoreboard players enable @a spawn
scoreboard players enable @a help
execute as @a[scores={spawn=1..}] run function tf:spawn
execute as @a[scores={help=1..}] run function tf:help
# inside the town wall: adventure mode (players tagged tf_builder keep theirs), back to survival outside
execute as @a[gamemode=survival,tag=!tf_builder,x=212,y=0,z=24,dx=64,dy=255,dz=64] run function tf:enter_town
execute as @a[tag=tf_town] unless entity @s[x=212,y=0,z=24,dx=64,dy=255,dz=64] run function tf:leave_town
# starter kit pad and the plates before the wild portal: whoever powers a plate (the nearest player; a plate
# notices every step, while positions checked once a tick miss players walking quickly); top of the tower
execute unless block 261 63 76 minecraft:light_weighted_pressure_plate[power=0] positioned 261.5 63 76.5 as @p[distance=..2] run function tf:kit
execute if block 275 63 55 minecraft:stone_pressure_plate[powered=true] positioned 275.5 63 55.5 as @p[distance=..3] at @s run function tf:wild
execute if block 275 63 56 minecraft:stone_pressure_plate[powered=true] positioned 275.5 63 56.5 as @p[distance=..3] at @s run function tf:wild
execute if block 275 63 57 minecraft:stone_pressure_plate[powered=true] positioned 275.5 63 57.5 as @p[distance=..3] at @s run function tf:wild
execute as @a[x=276,y=62,z=55,dx=0,dy=4,dz=2] at @s run function tf:wild
execute as @a[x=226,y=81,z=72,dx=2,dy=3,dz=2] run function tf:parkour
