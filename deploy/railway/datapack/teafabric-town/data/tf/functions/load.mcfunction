# Runs when the datapack loads (server start, /reload)
scoreboard objectives add tf_state dummy
scoreboard objectives add spawn trigger "回出生點"
scoreboard objectives add help trigger "說明"
execute unless score #build tf_state matches 2 run function tf:prepare
schedule function tf:second 20t replace
