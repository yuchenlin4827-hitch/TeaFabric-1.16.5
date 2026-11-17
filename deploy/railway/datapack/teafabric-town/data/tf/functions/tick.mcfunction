# Every tick
scoreboard players enable @a spawn
scoreboard players enable @a help
execute as @a[scores={spawn=1..}] run function tf:spawn
execute as @a[scores={help=1..}] run function tf:help
# inside the town wall: adventure mode (players tagged tf_builder keep theirs), back to survival outside
execute as @a[gamemode=survival,tag=!tf_builder,x=212,y=0,z=24,dx=64,dy=255,dz=64] run function tf:enter_town
execute as @a[tag=tf_town] unless entity @s[x=212,y=0,z=24,dx=64,dy=255,dz=64] run function tf:leave_town
# starter kit pad, wild portal, top of the parkour tower
execute as @a[x=261,y=63,z=76,dx=0,dy=0,dz=0] run function tf:kit
execute as @a[x=276,y=63,z=55,dx=0,dy=2,dz=2] at @s run function tf:wild
execute as @a[x=226,y=82,z=72,dx=2,dy=1,dz=2] run function tf:parkour
