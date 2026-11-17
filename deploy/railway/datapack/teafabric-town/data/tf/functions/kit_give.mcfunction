tag @s add tf_kit
give @s minecraft:stone_sword
give @s minecraft:stone_pickaxe
give @s minecraft:stone_axe
give @s minecraft:stone_shovel
give @s minecraft:leather_chestplate
give @s minecraft:leather_boots
give @s minecraft:bread 16
give @s minecraft:torch 16
give @s minecraft:white_bed
give @s minecraft:golden_shovel
give @s minecraft:emerald 5
give @s minecraft:written_book{title:"新手指南",author:"TeaFabric",pages:['{"text": "歡迎來到 TeaFabric！\\n\\n這本書介紹出生點的城鎮。\\n\\n城鎮在圍牆裡，裡面是冒險模式：可以用工作台、熔爐、附魔台，但方塊不能拆。走出城門就是一般生存。"}','{"text": "工作坊（西北）\\n工作台、熔爐、鐵砧、砂輪、釀造台、滿級附魔台、終界箱（個人倉庫）。\\n\\n商店街（東北）\\n把石頭、木頭、農作物、怪物掉落物賣給收購商換綠寶石，再買食物、工具、附魔書、下界材料。"}','{"text": "跑酷塔（西南）\\n爬到頂端第一次可以拿 3 顆鑽石。\\n\\n野外傳送門（東門）\\n走進去會隨機傳送到 2000 格內的荒野，適合找地方蓋家。\\n\\n回出生點\\n輸入 /trigger spawn"}','{"text": "常用指令\\n/spawn 回出生點\\n/sethome、/home 家（3 個）\\n/tpa 玩家、/tpaccept\\n/back 回上一個位置\\n/msg 玩家 私訊\\n/trigger help 說明\\n/changepassword 舊 新"}','{"text": "圈地保護\\n禮包裡的金鏟子：右鍵點兩個對角就圈好一塊地，別人不能拆、不能開箱子。/trust 玩家 讓朋友一起蓋，/abandonclaim 取消。可圈的格數會隨遊玩時間增加。\\n\\n墳墓：死掉的東西留在墳墓，右鍵拿回。"}','{"text": "一個人睡覺就能跳過夜晚。\\n\\n箱子、背包的空格快速點兩下會自動整理。\\n\\n下界在這個伺服器是關閉的，下界材料可以在商店街向炎婆婆買。\\n\\n祝玩得開心！"}']}
tellraw @s {"text":"領到新手禮包了！背包裡有一本新手指南。","color":"green"}
playsound minecraft:entity.player.levelup player @s
