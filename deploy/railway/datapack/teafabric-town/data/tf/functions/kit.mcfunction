execute if entity @s[tag=tf_kit] run title @s actionbar {"text":"你已經領過新手禮包了","color":"gray"}
execute if entity @s[tag=!tf_kit] run function tf:kit_give
