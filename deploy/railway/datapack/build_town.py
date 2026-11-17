#!/usr/bin/env python3
"""Generates the TeaFabric spawn town datapack (Minecraft 1.16.5, pack format 6).

    python3 deploy/railway/datapack/build_town.py

writes deploy/railway/datapack/teafabric-town/ and teafabric-town.zip next to this script. The server
installs the zip with DATAPACKS (see the README). The town is built once, by the load function, around the
existing spawn plaza (244 63 56); BUILD_VERSION decides whether it is built again after a change.

What it adds around the plaza, inside a walled square of 65 x 65 blocks:
  - workshop (crafting, furnaces, anvils, a full enchanting table, brewing, ender chests), north-west
  - market with six villager traders (materials for emeralds, emeralds for goods), north-east
  - newcomers' hall (rules, help, a starter kit once per player), south-east
  - parkour tower (3 diamonds once per player), south-west
  - wild portal in the east gate (random teleport into the wilderness), /trigger spawn back
  - fountain, trees, lamps, flowers, a wall with three gates
Inside the walls players are in adventure mode (they can use blocks but not break or place them), and hostile
mobs are removed; outside it is ordinary survival.
"""
import json
import os
import random
import shutil
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'teafabric-town')
ZIP = os.path.join(HERE, 'teafabric-town.zip')
BUILD_VERSION = 1

# The spawn plaza that is already there (built with console commands; see the README) and the town square.
SPAWN = (244, 63, 56)
PLAZA = (234, 46, 254, 66)            # x1 z1 x2 z2, floor y 62, hedge on its edge
TOWN = (212, 24, 276, 88)             # the wall runs on this edge
FLOOR = 62                            # ground level: blocks at y 62, players walk at y 63
TOWN_SEL = 'x=212,y=0,z=24,dx=64,dy=255,dz=64'

cmds = []
occupied = set()                      # (x, z) cells with something on them (no flowers there)


def c(s):
    cmds.append(s)


def occupy(x1, z1, x2, z2):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for z in range(min(z1, z2), max(z1, z2) + 1):
            occupied.add((x, z))


def fill(x1, y1, z1, x2, y2, z2, block, mode=''):
    """fill, split into boxes of at most 32768 blocks (the command's limit)."""
    xa, xb = sorted((x1, x2))
    ya, yb = sorted((y1, y2))
    za, zb = sorted((z1, z2))
    dx, dy, dz = xb - xa + 1, yb - ya + 1, zb - za + 1
    if dx * dy * dz <= 32768:
        c(f'fill {xa} {ya} {za} {xb} {yb} {zb} {block}' + (f' {mode}' if mode else ''))
    elif dx >= dy and dx >= dz:
        m = xa + dx // 2 - 1
        fill(xa, ya, za, m, yb, zb, block, mode)
        fill(m + 1, ya, za, xb, yb, zb, block, mode)
    elif dy >= dz:
        m = ya + dy // 2 - 1
        fill(xa, ya, za, xb, m, zb, block, mode)
        fill(xa, m + 1, za, xb, yb, zb, block, mode)
    else:
        m = za + dz // 2 - 1
        fill(xa, ya, za, xb, yb, m, block, mode)
        fill(xa, ya, m + 1, xb, yb, zb, block, mode)


def setblock(x, y, z, block):
    c(f'setblock {x} {y} {z} {block}')


def text(t, color=None, bold=False):
    d = {'text': t}
    if color:
        d['color'] = color
    if bold:
        d['bold'] = True
    return json.dumps(d, ensure_ascii=False)


def sign_nbt(lines):
    """Up to four lines: a string, or (string, colour), or (string, colour, bold)."""
    parts = []
    for i, line in enumerate(lines[:4]):
        if isinstance(line, str):
            line = (line,)
        parts.append(f"Text{i + 1}:'{text(*line)}'")
    return '{' + ','.join(parts) + '}'


def wall_sign(x, y, z, facing, lines, wood='oak'):
    setblock(x, y, z, f'minecraft:{wood}_wall_sign[facing={facing}]{sign_nbt(lines)}')


def standing_sign(x, y, z, rotation, lines, wood='oak'):
    """rotation: 0 faces south, 4 west, 8 north, 12 east (the side the text is read from)."""
    setblock(x, y, z, f'minecraft:{wood}_sign[rotation={rotation}]{sign_nbt(lines)}')
    occupy(x, z, x, z)


def lamp_post(x, z):
    setblock(x, 63, z, 'minecraft:stone_brick_wall')
    setblock(x, 64, z, 'minecraft:stone_brick_wall')
    setblock(x, 65, z, 'minecraft:lantern[hanging=false]')
    occupy(x, z, x, z)


def town_rects():
    """The town square without the plaza, as four rectangles (x1, z1, x2, z2)."""
    tx1, tz1, tx2, tz2 = TOWN
    px1, pz1, px2, pz2 = PLAZA
    return [(tx1, tz1, tx2, pz1 - 1), (tx1, pz2 + 1, tx2, tz2),
            (tx1, pz1, px1 - 1, pz2), (px2 + 1, pz1, tx2, pz2)]


# ---------------------------------------------------------------------------------------------- ground

def ground():
    c('# ground: everything above the floor cleared, holes below filled, grass on top (the plaza stays)')
    for x1, z1, x2, z2 in town_rects():
        fill(x1, 63, z1, x2, 120, z2, 'minecraft:air')
        for hole in ('air', 'cave_air', 'water', 'lava'):
            fill(x1, 52, z1, x2, 61, z2, 'minecraft:dirt', f'replace minecraft:{hole}')
        fill(x1, FLOOR, z1, x2, FLOOR, z2, 'minecraft:grass_block')
    occupy(*PLAZA)


def road_out(x1, z1, x2, z2, path_x1, path_z1, path_x2, path_z2):
    """A short road outside a gate, cut through whatever terrain is there."""
    fill(x1, 63, z1, x2, 90, z2, 'minecraft:air')
    for hole in ('air', 'cave_air', 'water', 'lava'):
        fill(x1, 55, z1, x2, 61, z2, 'minecraft:dirt', f'replace minecraft:{hole}')
    fill(x1, FLOOR, z1, x2, FLOOR, z2, 'minecraft:grass_block')
    fill(path_x1, FLOOR, path_z1, path_x2, FLOOR, path_z2, 'minecraft:grass_path')


def town_wall():
    c('# town wall: stone bricks, crenels, lanterns; gates north, south and west; the wild portal east')
    tx1, tz1, tx2, tz2 = TOWN
    for (x1, z1, x2, z2) in [(tx1, tz1, tx2, tz1), (tx1, tz2, tx2, tz2), (tx1, tz1, tx1, tz2), (tx2, tz1, tx2, tz2)]:
        fill(x1, 55, z1, x2, 65, z2, 'minecraft:stone_bricks')
        occupy(x1, z1, x2, z2)
    perimeter = [(x, tz1) for x in range(tx1, tx2 + 1)] + [(x, tz2) for x in range(tx1, tx2 + 1)] + \
                [(tx1, z) for z in range(tz1 + 1, tz2)] + [(tx2, z) for z in range(tz1 + 1, tz2)]
    for x, z in perimeter:
        if (x + z) % 2 == 0:
            setblock(x, 66, z, 'minecraft:stone_bricks')
            if x % 8 == 0 and z % 8 == 0 or (z in (tz1, tz2) and x % 8 == 0) or (x in (tx1, tx2) and z % 8 == 0):
                setblock(x, 67, z, 'minecraft:lantern[hanging=false]')
    gx1, gx2 = 243, 245          # north and south gates
    gz1, gz2 = 55, 57            # west gate and the east portal
    for z in (tz1, tz2):
        fill(gx1, 63, z, gx2, 65, z, 'minecraft:air')
        fill(gx1 - 1, 66, z, gx2 + 1, 66, z, 'minecraft:stone_bricks')
        setblock(gx1 - 1, 67, z, 'minecraft:lantern[hanging=false]')
        setblock(gx2 + 1, 67, z, 'minecraft:lantern[hanging=false]')
        fill(gx1, FLOOR, z, gx2, FLOOR, z, 'minecraft:grass_path')
    fill(tx1, 63, gz1, tx1, 65, gz2, 'minecraft:air')
    fill(tx1, 66, gz1 - 1, tx1, 66, gz2 + 1, 'minecraft:stone_bricks')
    setblock(tx1, 67, gz1 - 1, 'minecraft:lantern[hanging=false]')
    setblock(tx1, 67, gz2 + 1, 'minecraft:lantern[hanging=false]')
    fill(tx1, FLOOR, gz1, tx1, FLOOR, gz2, 'minecraft:grass_path')
    road_out(gx1 - 1, tz1 - 12, gx2 + 1, tz1 - 1, gx1, tz1 - 12, gx2, tz1 - 1)
    road_out(gx1 - 1, tz2 + 1, gx2 + 1, tz2 + 12, gx1, tz2 + 1, gx2, tz2 + 12)
    road_out(tx1 - 12, gz1 - 1, tx1 - 1, gz2 + 1, tx1 - 12, gz1, tx1 - 1, gz2)

    c('# the wild portal in the east gate: an obsidian frame with portal blocks (the Nether is off, so they only')
    c('# look like a portal; tf:tick sends whoever steps in to a random place in the wild)')
    x = tx2
    fill(x, 62, gz1 - 1, x, 66, gz2 + 1, 'minecraft:obsidian')
    fill(x, 63, gz1, x, 65, gz2, 'minecraft:air')
    setblock(x, 67, gz1 - 1, 'minecraft:lantern[hanging=false]')
    setblock(x, 67, gz2 + 1, 'minecraft:lantern[hanging=false]')
    for z in (gz1 - 1, gz2 + 1):
        setblock(x, 66, z, 'minecraft:crying_obsidian')
    # fill places every block before updating neighbours, so the portal sees a complete frame and stays
    fill(x, 63, gz1, x, 65, gz2, 'minecraft:nether_portal[axis=z]')


def paths():
    c('# paths from the plaza to the gates and the buildings')
    px1, pz1, px2, pz2 = PLAZA
    tx1, tz1, tx2, tz2 = TOWN
    p = 'minecraft:grass_path'
    for box in [(243, tz1 + 1, 245, pz1 - 1), (243, pz2 + 1, 245, tz2 - 1),
                (tx1 + 1, 55, px1 - 1, 57), (px2 + 1, 55, tx2 - 1, 57),
                (238, 35, 242, 37), (246, 35, 250, 37),        # workshop, market doors
                (246, 75, 250, 77), (232, 72, 242, 74)]:       # newcomers' hall door, parkour tower
        x1, z1, x2, z2 = box
        fill(x1, FLOOR, z1, x2, FLOOR, z2, p)
        occupy(x1, z1, x2, z2)
    for z in (27, 33, 41):
        lamp_post(242, z)
        lamp_post(246, z)
    for z in (69, 80, 86):
        lamp_post(242, z)
        lamp_post(246, z)
    for x in (216, 224, 231):
        lamp_post(x, 54)
        lamp_post(x, 58)
    for x in (258, 264, 270):
        lamp_post(x, 54)
        lamp_post(x, 58)


# ---------------------------------------------------------------------------------------------- buildings

def building(x1, z1, x2, z2, m, doors, windows, pillars_x, pillars_z, lights):
    """A hall with a gable roof along x. m: materials; doors: (side, a, b) openings 3 high; windows: lists of
    (side, a, b) at y 65-66; side is 'n' (z1), 's' (z2), 'w' (x1) or 'e' (x2)."""
    occupy(x1 - 1, z1 - 1, x2 + 1, z2 + 1)
    top = 67                                       # walls y 63-67
    fill(x1, FLOOR, z1, x2, FLOOR, z2, m['base'])
    fill(x1 + 1, FLOOR, z1 + 1, x2 - 1, FLOOR, z2 - 1, m['floor'])
    fill(x1, 63, z1, x2, top, z1, m['wall'])
    fill(x1, 63, z2, x2, top, z2, m['wall'])
    fill(x1, 63, z1, x1, top, z2, m['wall'])
    fill(x2, 63, z1, x2, top, z2, m['wall'])
    fill(x1, 63, z1, x2, 63, z1, m['base'])
    fill(x1, 63, z2, x2, 63, z2, m['base'])
    fill(x1, 63, z1, x1, 63, z2, m['base'])
    fill(x2, 63, z1, x2, 63, z2, m['base'])
    for x in pillars_x:
        fill(x, 63, z1, x, top, z1, m['pillar'])
        fill(x, 63, z2, x, top, z2, m['pillar'])
    for z in pillars_z:
        fill(x1, 63, z, x1, top, z, m['pillar'])
        fill(x2, 63, z, x2, top, z, m['pillar'])
    fill(x1 + 1, 63, z1 + 1, x2 - 1, 90, z2 - 1, 'minecraft:air')
    for side, a, b in windows:
        if side in 'ns':
            z = z1 if side == 'n' else z2
            fill(a, 65, z, b, 66, z, 'minecraft:glass_pane[east=true,west=true]')
        else:
            x = x1 if side == 'w' else x2
            fill(x, 65, a, x, 66, b, 'minecraft:glass_pane[north=true,south=true]')
    for side, a, b in doors:
        if side in 'ns':
            z = z1 if side == 'n' else z2
            fill(a, 63, z, b, 65, z, 'minecraft:air')
            fill(a, FLOOR, z, b, FLOOR, z, m['floor'])
        else:
            x = x1 if side == 'w' else x2
            fill(x, 63, a, x, 65, b, 'minecraft:air')
            fill(x, FLOOR, a, x, FLOOR, b, m['floor'])
    # gable roof: stairs from both long sides up to a ridge of slabs, gable ends filled
    i = 0
    while True:
        zn, zs, y = z1 - 1 + i, z2 + 1 - i, top + i
        if zn > zs:
            break
        if zn == zs:
            fill(x1 - 1, y, zn, x2 + 1, y, zn, m['slab'])
            break
        fill(x1 - 1, y, zn, x2 + 1, y, zn, f"{m['stairs']}[facing=south]")
        fill(x1 - 1, y, zs, x2 + 1, y, zs, f"{m['stairs']}[facing=north]")
        if i > 0 and zs - zn >= 2:
            fill(x1, y, zn + 1, x1, y, zs - 1, m['gable'])
            fill(x2, y, zn + 1, x2, y, zs - 1, m['gable'])
        i += 1
    for x, z in lights:
        setblock(x, FLOOR, z, 'minecraft:sea_lantern')


def workshop():
    c('# workshop (north-west): crafting, smelting, anvils, enchanting with 15 bookshelves, brewing, ender chests')
    x1, z1, x2, z2 = 217, 29, 237, 43
    building(x1, z1, x2, z2,
             dict(base='minecraft:stone_bricks', floor='minecraft:spruce_planks', wall='minecraft:spruce_planks',
                  pillar='minecraft:spruce_log', stairs='minecraft:spruce_stairs', slab='minecraft:spruce_slab',
                  gable='minecraft:spruce_planks'),
             doors=[('e', 35, 37)],
             windows=[('n', 219, 220), ('n', 224, 225), ('n', 229, 230), ('n', 234, 235),
                      ('s', 219, 220), ('s', 224, 225), ('s', 229, 230), ('s', 234, 235),
                      ('w', 32, 33), ('w', 39, 40), ('e', 31, 32), ('e', 40, 41)],
             pillars_x=[217, 222, 227, 232, 237], pillars_z=[29, 36, 43],
             lights=[(220, 31), (220, 41), (226, 33), (226, 39), (232, 33), (232, 39), (236, 36)])
    # north row
    for x in (226, 227, 228):
        setblock(x, 63, 30, 'minecraft:crafting_table')
    for x in (229, 230, 231):
        setblock(x, 63, 30, 'minecraft:furnace[facing=south]')
    setblock(232, 63, 30, 'minecraft:blast_furnace[facing=south]')
    setblock(233, 63, 30, 'minecraft:smoker[facing=south]')
    setblock(234, 63, 30, 'minecraft:stonecutter[facing=south]')
    setblock(235, 63, 30, 'minecraft:loom[facing=south]')
    setblock(236, 63, 30, 'minecraft:cartography_table')
    # south row
    setblock(226, 63, 42, 'minecraft:anvil[facing=east]')
    setblock(227, 63, 42, 'minecraft:anvil[facing=east]')
    setblock(228, 63, 42, 'minecraft:grindstone[face=floor,facing=north]')
    setblock(229, 63, 42, 'minecraft:smithing_table')
    setblock(230, 63, 42, 'minecraft:brewing_stand')
    setblock(231, 63, 42, 'minecraft:cauldron[level=3]')
    setblock(232, 63, 42, 'minecraft:fletching_table')
    for x in (233, 234, 235, 236):
        setblock(x, 63, 42, 'minecraft:ender_chest[facing=north]')
    # a crafting table in the middle of the room too
    setblock(229, 63, 36, 'minecraft:crafting_table')
    setblock(231, 63, 36, 'minecraft:crafting_table')
    # enchanting: table with bookshelves two blocks away on every side but the entrance (15 = level 30)
    ex, ez = 221, 36
    setblock(ex, 63, ez, 'minecraft:enchanting_table')
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if max(abs(dx), abs(dz)) == 2 and (dx, dz) != (2, 0):
                setblock(ex + dx, 63, ez + dz, 'minecraft:bookshelf')
    setblock(ex, 65, ez - 2, 'minecraft:lantern[hanging=false]')
    setblock(ex, 64, ez - 2, 'minecraft:bookshelf')
    wall_sign(227, 65, 30, 'south', [('工作坊', 'gold', True), '工作台・熔爐', '鐵砧・砂輪・釀造', '大家都能用'], 'spruce')
    wall_sign(218, 65, 36, 'east', [('附魔台', 'light_purple', True), '書架已經擺滿', '最高 30 級', ''], 'spruce')
    wall_sign(236, 65, 42, 'west', [('終界箱', 'dark_purple', True), '每個人的箱子', '都是分開的', '可以當倉庫'], 'spruce')


def offer(buy, sell, buy_b=None):
    def item(i):
        id_, n = i[0], i[1]
        s = f'id:"minecraft:{id_}",Count:{n}b'
        if len(i) > 2:
            s += f',tag:{i[2]}'
        return '{' + s + '}'
    s = f'buy:{item(buy)},sell:{item(sell)},'
    if buy_b:
        s += f'buyB:{item(buy_b)},'
    return '{' + s + 'maxUses:2147483647,uses:0,rewardExp:0b,xp:0,priceMultiplier:0.0f,specialPrice:0,demand:0}'


def book(ench, lvl):
    return ('enchanted_book', 1, '{StoredEnchantments:[{id:"minecraft:%s",lvl:%ds}]}' % (ench, lvl))


E = 'emerald'
SHOPS = [
    ('老石・礦石收購', 'gray', 'mason', 'plains', [
        offer(('cobblestone', 32), (E, 1)), offer(('coal', 12), (E, 1)), offer(('iron_ingot', 4), (E, 1)),
        offer(('gold_ingot', 3), (E, 1)), offer(('redstone', 16), (E, 1)), offer(('lapis_lazuli', 8), (E, 1)),
        offer(('diamond', 1), (E, 3))]),
    ('林伯・農林收購', 'green', 'farmer', 'plains', [
        offer(('oak_log', 16), (E, 1)), offer(('birch_log', 16), (E, 1)), offer(('spruce_log', 16), (E, 1)),
        offer(('wheat', 20), (E, 1)), offer(('carrot', 22), (E, 1)), offer(('potato', 22), (E, 1)),
        offer(('pumpkin', 6), (E, 1)), offer(('sugar_cane', 24), (E, 1))]),
    ('夜巡・怪物掉落收購', 'dark_aqua', 'cleric', 'swamp', [
        offer(('rotten_flesh', 24), (E, 1)), offer(('bone', 16), (E, 1)), offer(('string', 16), (E, 1)),
        offer(('gunpowder', 8), (E, 1)), offer(('spider_eye', 8), (E, 1)), offer(('ender_pearl', 1), (E, 1))]),
    ('小美・雜貨店', 'yellow', 'butcher', 'plains', [
        offer((E, 1), ('bread', 8)), offer((E, 1), ('cooked_beef', 6)), offer((E, 1), ('torch', 16)),
        offer((E, 2), ('white_bed', 1)), offer((E, 1), ('oak_planks', 32)), offer((E, 2), ('glass', 16)),
        offer((E, 3), ('bucket', 1)), offer((E, 1), ('arrow', 16)), offer((E, 2), ('lead', 1)),
        offer((E, 4), ('name_tag', 1)), offer((E, 6), ('saddle', 1)), offer((E, 3), ('ender_pearl', 2)),
        offer((E, 8), ('golden_apple', 1))]),
    ('炎婆婆・下界雜貨', 'red', 'cleric', 'desert', [
        offer((E, 3), ('blaze_rod', 1)), offer((E, 1), ('nether_wart', 4)), offer((E, 1), ('glowstone_dust', 8)),
        offer((E, 1), ('quartz', 8)), offer((E, 2), ('netherrack', 16)), offer((E, 1), ('soul_sand', 4)),
        offer((E, 4), ('ghast_tear', 1)), offer((E, 2), ('magma_cream', 1)), offer((E, 16), ('netherite_scrap', 1))]),
    ('書蟲・附魔書', 'light_purple', 'librarian', 'plains', [
        offer((E, 2), ('book', 1)),
        offer((E, 24), book('mending', 1), ('book', 1)), offer((E, 12), book('unbreaking', 3), ('book', 1)),
        offer((E, 12), book('efficiency', 4), ('book', 1)), offer((E, 16), book('fortune', 2), ('book', 1)),
        offer((E, 16), book('silk_touch', 1), ('book', 1)), offer((E, 12), book('protection', 3), ('book', 1)),
        offer((E, 12), book('sharpness', 4), ('book', 1)), offer((E, 16), book('looting', 2), ('book', 1)),
        offer((E, 8), book('feather_falling', 4), ('book', 1)), offer((E, 10), book('power', 4), ('book', 1)),
        offer((E, 20), book('infinity', 1), ('book', 1))]),
]


def market():
    c('# market (north-east): six traders behind a counter; emeralds are the currency')
    x1, z1, x2, z2 = 251, 29, 271, 43
    building(x1, z1, x2, z2,
             dict(base='minecraft:stone_bricks', floor='minecraft:oak_planks', wall='minecraft:birch_planks',
                  pillar='minecraft:dark_oak_log', stairs='minecraft:dark_oak_stairs', slab='minecraft:dark_oak_slab',
                  gable='minecraft:birch_planks'),
             doors=[('w', 35, 37)],
             windows=[('s', 253, 254), ('s', 258, 259), ('s', 263, 264), ('s', 268, 269),
                      ('w', 31, 32), ('w', 40, 41), ('e', 32, 33), ('e', 39, 40)],
             pillars_x=[251, 256, 261, 266, 271], pillars_z=[29, 36, 43],
             lights=[(253, 40), (258, 38), (263, 40), (268, 38), (256, 35), (266, 35)])
    stalls = [254, 257, 260, 263, 266, 269]
    for x in range(252, 271):
        if x not in stalls:
            setblock(x, 63, 31, 'minecraft:barrel[facing=up]')
        setblock(x, 63, 32, 'minecraft:oak_slab')
        setblock(x, 66, 32, 'minecraft:red_wool' if x % 2 else 'minecraft:white_wool')
    c('kill @e[type=minecraft:villager,tag=tf_shop]')
    for x, (name, color, prof, vtype, offers) in zip(stalls, SHOPS):
        c(f'summon minecraft:villager {x}.5 63 31.5 {{Tags:["tf_shop"],NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,'
          f'Silent:1b,CustomNameVisible:1b,CustomName:\'{text(name, color)}\',Rotation:[0.0f,0.0f],'
          f'VillagerData:{{profession:"minecraft:{prof}",level:5,type:"minecraft:{vtype}"}},'
          f'Offers:{{Recipes:[{",".join(offers)}]}}}}')
        short = name.split('・')
        wall_sign(x, 65, 30, 'south', [(short[0], color, True), short[1], '', '右鍵交易'], 'dark_oak')
    wall_sign(252, 65, 34, 'east', [('商店街', 'gold', True), '用綠寶石交易', '先把材料賣給', '收購商換綠寶石'], 'dark_oak')


def newcomers_hall():
    c("# newcomers' hall (south-east): rules, help and the starter kit pad")
    x1, z1, x2, z2 = 251, 69, 271, 83
    building(x1, z1, x2, z2,
             dict(base='minecraft:stone_bricks', floor='minecraft:oak_planks', wall='minecraft:bricks',
                  pillar='minecraft:oak_log', stairs='minecraft:stone_brick_stairs', slab='minecraft:stone_brick_slab',
                  gable='minecraft:bricks'),
             doors=[('w', 75, 77)],
             windows=[('s', 253, 254), ('s', 258, 259), ('s', 263, 264), ('s', 268, 269)],
             pillars_x=[251, 256, 261, 266, 271], pillars_z=[69, 76, 83],
             lights=[(254, 72), (254, 80), (268, 72), (268, 80)])
    fill(252, 63, 82, 270, 63, 82, 'minecraft:bookshelf')
    fill(259, 63, 74, 263, 63, 78, 'minecraft:red_carpet')
    setblock(261, 63, 76, 'minecraft:air')
    setblock(261, FLOOR, 76, 'minecraft:gold_block')
    setblock(261, 63, 76, 'minecraft:light_weighted_pressure_plate')
    for x, z in ((259, 74), (263, 74), (259, 78), (263, 78)):
        setblock(x, 63, z, 'minecraft:lantern[hanging=false]')
    standing_sign(264, 63, 76, 4, [('新手禮包', 'gold', True), '踩上金色', '壓力板領取', ('每人一次', 'gray')])
    rules = [
        [('伺服器規則', 'red', True), '不破壞別人的', '建築和農田', '不偷東西'],
        [('伺服器規則', 'red', True), '聊天要友善', '不洗頻', '不用外掛'],
        [('城鎮裡', 'gold', True), '是冒險模式', '方塊不能拆', '工作台能用'],
    ]
    for x, lines in zip((254, 256, 258), rules):
        wall_sign(x, 65, 70, 'south', lines)
    help_signs = [
        [('回出生點', 'aqua', True), '輸入指令', ('/trigger spawn', 'yellow'), ''],
        [('所有說明', 'aqua', True), '輸入指令', ('/trigger help', 'yellow'), ''],
        [('去野外', 'aqua', True), '走進東門的', '傳送門，隨機', '傳送到荒野'],
    ]
    for x, lines in zip((264, 266, 268), help_signs):
        wall_sign(x, 65, 70, 'south', lines)
    login = [
        [('帳號', 'gold', True), '第一次進來', ('/register', 'yellow'), '密碼 密碼'],
        [('帳號', 'gold', True), '之後每次', ('/login 密碼', 'yellow'), ''],
        [('帳號', 'gold', True), '改密碼', ('/changepassword', 'yellow'), '舊密碼 新密碼'],
    ]
    for z, lines in zip((74, 76, 78), login):
        wall_sign(270, 65, z, 'west', lines)


def parkour_tower():
    c('# parkour tower (south-west): coloured blocks spiral up around a pillar; the top gives 3 diamonds once')
    cx, cz = 227, 73
    occupy(cx - 4, cz - 4, cx + 4, cz + 4)
    fill(cx - 4, FLOOR, cz - 4, cx + 4, FLOOR, cz + 4, 'minecraft:hay_block')   # softer landing
    fill(cx - 1, FLOOR, cz - 1, cx + 1, 80, cz + 1, 'minecraft:polished_andesite')
    fill(cx - 1, 81, cz - 1, cx + 1, 81, cz + 1, 'minecraft:gold_block')
    ring = [(3, d) for d in range(-3, 3)] + [(d, 3) for d in range(3, -3, -1)] + \
           [(-3, d) for d in range(3, -3, -1)] + [(d, -3) for d in range(-3, 3)]
    colours = ['red', 'orange', 'yellow', 'lime', 'light_blue', 'blue', 'purple', 'magenta']
    start = 3                                       # (3, 0): the east side, odd so that no jump lands on a corner
    for k in range(19):                             # 19 jumps, one block up each: the last is level with the top
        dx, dz = ring[(start + 2 * k) % len(ring)]
        setblock(cx + dx, 63 + k, cz + dz, f'minecraft:{colours[k % len(colours)]}_wool')
    standing_sign(233, 63, 71, 12, [('跑酷塔', 'gold', True), '從東邊開始', '爬到金色頂端', ('3 顆鑽石・每人一次', 'aqua')])


def fountain(cx, cz):
    occupy(cx - 2, cz - 2, cx + 2, cz + 2)
    fill(cx - 2, FLOOR, cz - 2, cx + 2, 63, cz + 2, 'minecraft:stone_bricks')
    fill(cx - 1, 63, cz - 1, cx + 1, 63, cz + 1, 'minecraft:water')
    fill(cx, 63, cz, cx, 65, cz, 'minecraft:chiseled_stone_bricks')
    setblock(cx, 66, cz, 'minecraft:water')
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        setblock(cx + dx, 64, cz + dz, 'minecraft:lantern[hanging=false]')


def tree(x, z, log='oak_log', leaves='oak_leaves'):
    occupy(x - 2, z - 2, x + 2, z + 2)
    lv = f'minecraft:{leaves}[persistent=true]'
    fill(x - 2, 66, z - 2, x + 2, 67, z + 2, lv)
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        setblock(x + dx, 67, z + dz, 'minecraft:air')
    fill(x - 1, 68, z - 1, x + 1, 68, z + 1, lv)
    fill(x, 69, z - 1, x, 69, z + 1, lv)
    fill(x - 1, 69, z, x + 1, 69, z, lv)
    fill(x, 63, z, x, 68, z, f'minecraft:{log}')


def decorations():
    c('# fountain, trees, signposts in the plaza, flowers')
    fountain(262, 50)
    for x, z, kind in [(218, 49, 'oak'), (230, 50, 'birch'), (250, 49, 'oak'), (271, 49, 'birch'),
                       (218, 63, 'birch'), (229, 62, 'oak'), (259, 62, 'oak'), (271, 62, 'birch'),
                       (217, 83, 'oak'), (236, 84, 'birch')]:
        tree(x, z, f'{kind}_log', f'{kind}_leaves')
    standing_sign(246, 63, 47, 0, [('↑ 北', 'gold', True), '左：工作坊', '右：商店街', '・北門'])
    standing_sign(242, 63, 65, 8, [('↓ 南', 'gold', True), '左：新手服務處', '右：跑酷塔', '・南門'])
    standing_sign(235, 63, 58, 12, [('← 西', 'gold', True), '西門', '出城', ''])
    standing_sign(253, 63, 54, 4, [('→ 東', 'gold', True), '野外傳送門', ('走進去會隨機', 'gray'), ('傳送到荒野', 'gray')])
    standing_sign(272, 63, 54, 4, [('野外傳送門', 'light_purple', True), '隨機傳送到荒野', '回城請輸入', ('/trigger spawn', 'yellow')])
    rnd = random.Random(1165)
    flowers = ['poppy', 'dandelion', 'cornflower', 'oxeye_daisy', 'allium', 'azure_bluet', 'lily_of_the_valley']
    tx1, tz1, tx2, tz2 = TOWN
    for x in range(tx1 + 1, tx2):
        for z in range(tz1 + 1, tz2):
            if (x, z) in occupied:
                continue
            r = rnd.random()
            if r < 0.05:
                setblock(x, 63, z, f'minecraft:{rnd.choice(flowers)}')
            elif r < 0.13:
                setblock(x, 63, z, 'minecraft:grass')


def build_function():
    c(f'# Builds the town (generated by build_town.py; run by tf:load while #build tf_state is not {BUILD_VERSION})')
    c('tellraw @a {"text":"[TeaFabric] 正在建造城鎮…","color":"gray"}')
    ground()
    town_wall()
    paths()
    workshop()
    market()
    newcomers_hall()
    parkour_tower()
    decorations()
    c('kill @e[type=minecraft:item,x=196,y=40,z=8,dx=96,dy=100,dz=96]')
    c(f'scoreboard players set #build tf_state {BUILD_VERSION}')
    c('tellraw @a {"text":"[TeaFabric] 城鎮建好了","color":"gray"}')


# ---------------------------------------------------------------------------------------------- functions

GUIDE_PAGES = [
    '歡迎來到 TeaFabric！\n\n這本書介紹出生點的城鎮。\n\n城鎮在圍牆裡，裡面是冒險模式：可以用工作台、熔爐、附魔台，但方塊不能拆。走出城門就是一般生存。',
    '工作坊（西北）\n工作台、熔爐、鐵砧、砂輪、釀造台、滿級附魔台、終界箱（個人倉庫）。\n\n商店街（東北）\n把石頭、木頭、農作物、怪物掉落物賣給收購商換綠寶石，再買食物、工具、附魔書、下界材料。',
    '跑酷塔（西南）\n爬到頂端第一次可以拿 3 顆鑽石。\n\n野外傳送門（東門）\n走進去會隨機傳送到 2000 格內的荒野，適合找地方蓋家。\n\n回出生點\n輸入 /trigger spawn',
    '指令\n/trigger spawn 回出生點\n/trigger help 說明\n/changepassword 舊 新\n\n下界在這個伺服器是關閉的，下界材料可以在商店街向炎婆婆買。\n\n祝玩得開心！',
]


def write_functions():
    fn = {}
    fn['build'] = '\n'.join(cmds)
    fn['load'] = f'''# Runs when the datapack loads (server start, /reload)
scoreboard objectives add tf_state dummy
scoreboard objectives add spawn trigger "回出生點"
scoreboard objectives add help trigger "說明"
execute unless score #build tf_state matches {BUILD_VERSION} run function tf:build
schedule function tf:second 20t replace'''
    fn['tick'] = f'''# Every tick
scoreboard players enable @a spawn
scoreboard players enable @a help
execute as @a[scores={{spawn=1..}}] run function tf:spawn
execute as @a[scores={{help=1..}}] run function tf:help
# inside the town wall: adventure mode (players tagged tf_builder keep theirs), back to survival outside
execute as @a[gamemode=survival,tag=!tf_builder,{TOWN_SEL}] run function tf:enter_town
execute as @a[tag=tf_town] unless entity @s[{TOWN_SEL}] run function tf:leave_town
# starter kit pad, wild portal, top of the parkour tower
execute as @a[x=261,y=63,z=76,dx=0,dy=0,dz=0] run function tf:kit
execute as @a[x=276,y=63,z=55,dx=0,dy=2,dz=2] at @s run function tf:wild
execute as @a[x=226,y=82,z=72,dx=2,dy=1,dz=2] run function tf:parkour'''
    fn['second'] = f'''# Every second: hostile mobs inside the town wall are removed (dropped below the world)
execute as @e[type=#tf:hostile,{TOWN_SEL}] at @s run tp @s ~ -100 ~
schedule function tf:second 20t replace'''
    fn['enter_town'] = '''gamemode adventure @s
tag @s add tf_town'''
    fn['leave_town'] = '''gamemode survival @s[gamemode=adventure]
tag @s remove tf_town'''
    fn['spawn'] = '''scoreboard players set @s spawn 0
execute in minecraft:overworld run tp @s 244.5 63 56.5 0 0
tellraw @s {"text":"已傳送回出生點","color":"green"}'''
    fn['help'] = '''scoreboard players set @s help 0
tellraw @s {"text":"—— TeaFabric 伺服器 ——","color":"gold","bold":true}
tellraw @s [{"text":"/trigger spawn","color":"yellow"},{"text":"  回出生點（城鎮中心）","color":"white"}]
tellraw @s [{"text":"工作坊","color":"aqua"},{"text":"（西北）工作台、熔爐、鐵砧、滿級附魔台、終界箱","color":"white"}]
tellraw @s [{"text":"商店街","color":"aqua"},{"text":"（東北）材料換綠寶石，綠寶石換食物、附魔書、下界材料","color":"white"}]
tellraw @s [{"text":"新手服務處","color":"aqua"},{"text":"（東南）規則說明，踩金色壓力板領新手禮包（一次）","color":"white"}]
tellraw @s [{"text":"跑酷塔","color":"aqua"},{"text":"（西南）登頂第一次送 3 顆鑽石","color":"white"}]
tellraw @s [{"text":"野外傳送門","color":"aqua"},{"text":"（東門）隨機傳送到荒野","color":"white"}]
tellraw @s {"text":"城鎮裡是冒險模式（方塊不能拆），出城門就是一般生存。","color":"gray"}'''
    pages = ','.join("'" + json.dumps({'text': p}, ensure_ascii=False).replace('\\', '\\\\').replace("'", "\\'") + "'"
                     for p in GUIDE_PAGES)
    fn['kit'] = '''execute if entity @s[tag=tf_kit] run title @s actionbar {"text":"你已經領過新手禮包了","color":"gray"}
execute if entity @s[tag=!tf_kit] run function tf:kit_give'''
    fn['kit_give'] = f'''tag @s add tf_kit
give @s minecraft:stone_sword
give @s minecraft:stone_pickaxe
give @s minecraft:stone_axe
give @s minecraft:stone_shovel
give @s minecraft:leather_chestplate
give @s minecraft:leather_boots
give @s minecraft:bread 16
give @s minecraft:torch 16
give @s minecraft:white_bed
give @s minecraft:emerald 5
give @s minecraft:written_book{{title:"新手指南",author:"TeaFabric",pages:[{pages}]}}
tellraw @s {{"text":"領到新手禮包了！背包裡有一本新手指南。","color":"green"}}
playsound minecraft:entity.player.levelup player @s'''
    fn['wild'] = '''spreadplayers 244 56 0 2000 false @s
effect give @s minecraft:resistance 10 4 true
tellraw @s [{"text":"已傳送到荒野。回出生點：","color":"green"},{"text":"/trigger spawn","color":"yellow"}]
playsound minecraft:entity.enderman.teleport player @s'''
    fn['parkour'] = '''execute if entity @s[tag=!tf_parkour] run function tf:parkour_reward
execute if entity @s[tag=tf_parkour_seen] run title @s actionbar {"text":"獎勵每人只有一次","color":"gray"}
tag @s add tf_parkour_seen
tp @s 227.5 63 80.5 180 0'''
    fn['parkour_reward'] = '''tag @s add tf_parkour
give @s minecraft:diamond 3
tellraw @a [{"selector":"@s","color":"aqua"},{"text":" 爬上了跑酷塔！","color":"gold"}]
playsound minecraft:ui.toast.challenge_complete player @s'''

    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(os.path.join(OUT, 'data/tf/functions'))
    os.makedirs(os.path.join(OUT, 'data/tf/tags/entity_types'))
    os.makedirs(os.path.join(OUT, 'data/minecraft/tags/functions'))
    with open(os.path.join(OUT, 'pack.mcmeta'), 'w', encoding='utf-8') as f:
        json.dump({'pack': {'pack_format': 6, 'description': 'TeaFabric spawn town'}}, f, ensure_ascii=False, indent=1)
    for name, body in fn.items():
        with open(os.path.join(OUT, 'data/tf/functions', name + '.mcfunction'), 'w', encoding='utf-8') as f:
            f.write(body + '\n')
    for tag, value in (('load', 'tf:load'), ('tick', 'tf:tick')):
        with open(os.path.join(OUT, 'data/minecraft/tags/functions', tag + '.json'), 'w') as f:
            json.dump({'values': [value]}, f)
    hostile = ['zombie', 'husk', 'drowned', 'zombie_villager', 'skeleton', 'stray', 'creeper', 'spider',
               'cave_spider', 'witch', 'enderman', 'slime', 'phantom', 'pillager', 'vindicator', 'evoker', 'ravager',
               'vex', 'illusioner', 'silverfish', 'endermite', 'zombified_piglin']
    with open(os.path.join(OUT, 'data/tf/tags/entity_types/hostile.json'), 'w') as f:
        json.dump({'values': ['minecraft:' + h for h in hostile]}, f, indent=1)
    if os.path.exists(ZIP):
        os.remove(ZIP)
    with zipfile.ZipFile(ZIP, 'w', zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(OUT):
            for name in sorted(files):
                p = os.path.join(root, name)
                info = zipfile.ZipInfo(os.path.relpath(p, OUT).replace(os.sep, '/'), (2020, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                with open(p, 'rb') as f:
                    z.writestr(info, f.read())


if __name__ == '__main__':
    build_function()
    write_functions()
    print(f'{len(cmds)} build commands; wrote {OUT} and {ZIP}')
