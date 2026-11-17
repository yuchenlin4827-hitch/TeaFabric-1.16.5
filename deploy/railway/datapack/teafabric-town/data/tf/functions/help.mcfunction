scoreboard players set @s help 0
tellraw @s {"text":"—— TeaFabric 伺服器 ——","color":"gold","bold":true}
tellraw @s [{"text":"/spawn","color":"yellow"},{"text":" 或 ","color":"white"},{"text":"/trigger spawn","color":"yellow"},{"text":"  回出生點（城鎮中心）","color":"white"}]
tellraw @s [{"text":"/sethome 名字","color":"yellow"},{"text":"、","color":"white"},{"text":"/home 名字","color":"yellow"},{"text":"  設定、回到家（最多 3 個）","color":"white"}]
tellraw @s [{"text":"/tpa 玩家","color":"yellow"},{"text":" 請求傳送到對方，對方輸入 ","color":"white"},{"text":"/tpaccept","color":"yellow"},{"text":" 接受","color":"white"}]
tellraw @s [{"text":"/back","color":"yellow"},{"text":"  回到上一個位置或死亡地點；","color":"white"},{"text":"/msg 玩家 訊息","color":"yellow"},{"text":" 私訊","color":"white"}]
tellraw @s [{"text":"圈地","color":"aqua"},{"text":"：拿金鏟子右鍵點兩個對角，裡面別人不能動；","color":"white"},{"text":"/trust 玩家","color":"yellow"},{"text":" 讓朋友一起蓋","color":"white"}]
tellraw @s [{"text":"墳墓","color":"aqua"},{"text":"：死掉的東西留在墳墓裡，右鍵拿回。","color":"white"},{"text":"睡覺","color":"aqua"},{"text":"：一個人睡就能跳過夜晚。","color":"white"},{"text":"整理","color":"aqua"},{"text":"：在箱子或背包的空格快速點兩下。","color":"white"}]
tellraw @s [{"text":"工作坊","color":"aqua"},{"text":"（西北）工作台、熔爐、鐵砧、滿級附魔台、終界箱","color":"white"}]
tellraw @s [{"text":"商店街","color":"aqua"},{"text":"（東北）材料換綠寶石，綠寶石換食物、附魔書、下界材料","color":"white"}]
tellraw @s [{"text":"新手服務處","color":"aqua"},{"text":"（東南）規則說明，踩金色壓力板領新手禮包（一次）","color":"white"}]
tellraw @s [{"text":"跑酷塔","color":"aqua"},{"text":"（西南）登頂第一次送 3 顆鑽石","color":"white"}]
tellraw @s [{"text":"野外傳送門","color":"aqua"},{"text":"（東門）隨機傳送到荒野","color":"white"}]
tellraw @s {"text":"城鎮裡是冒險模式（方塊不能拆），出城門就是一般生存。","color":"gray"}
