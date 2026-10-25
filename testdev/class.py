#!/usr/bin/python3

class MyClass:
    """一个简单的类实例"""
    i = 12345
    def f(self):
        return 'hello world'

# 实例化类
x = MyClass()

# 访问类的属性和方法
print("MyClass 类的属性 i 为：", x.i)
print("MyClass 类的方法 f 输出为：", x.f())



class MyClass2:
    """我的类实例 """
    i= 11112
    def pri(self):
        return "输出一句话。"


# 实例化

y = MyClass2()

# 访问类的属性和方法
print("属性：",y.i)
print("方法：",y.pri())