# 模拟地球环绕插件！适用于标准世界地图

**问：什么叫“标准地图”**

将MC地图带入经纬度坐标系，Z轴代表南北方向，X轴代表东西方向，只要你的地图不是“旋转90度”的都行（没人会做这种SB地图吧，MC的太阳和月亮可是固定的）

## 更真实的模拟绕地球一圈

灵感来源于[WorldBorder](https://github.com/PryPurity/WorldBorder)，但在越过边界后的传送效果更好

### Z坐标越界传送

**碰到Z轴边界就会往东或者西传送一半地图宽度的距离**

（这在地理上叫做“对侧经线”概念，比如你跨越极点就会进另一条经线，刚好是 “180°-上一条经度” 。在MC里360°抽象成整个地图宽度，那“180°”就是地图一半宽）

同时将玩家/NPC的朝向翻转180°使其朝南

### X坐标越界传送

碰X轴边界就很简单，直接不改变朝向传到地图另一边就好了，和[WorldBorder](https://github.com/PryPurity/WorldBorder)插件是一样的处理

## 支持所有实体

任何实体（包括掉落物、射出的箭）触碰边界即可传送。如果坐着载具（比如骑猪、马、船、矿车），则可以连同载具带人一起传送，并且**保持骑乘姿态**

反观[WorldBorder](https://github.com/PryPurity/WorldBorder)只是让玩家解除骑乘状态并传送，无法保持骑乘状态

# 已知问题

## 恢复骑乘绑定BUG

在本地服务端测试完全没有问题。但是放在真服务器上，如果玩家坐着载具，则大概率不能完美过渡，例如：要么人送走了船没走，要么船先走了把人丢下。
