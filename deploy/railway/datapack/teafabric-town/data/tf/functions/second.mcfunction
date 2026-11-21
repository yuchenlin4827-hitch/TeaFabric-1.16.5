# Every second: hostile mobs inside the town wall are removed (dropped below the world)
execute as @e[type=#tf:hostile,x=212,y=0,z=24,dx=64,dy=255,dz=64] at @s run tp @s ~ -100 ~
schedule function tf:second 20t replace
