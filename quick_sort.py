"""
快速排序（Quick Sort）实现
时间复杂度：平均 O(n log n)，最坏 O(n²)
空间复杂度：O(log n)（递归栈空间）
"""

def quick_sort(arr):
    """
    快速排序的入口函数
    
    参数:
        arr: 待排序的列表
    
    返回:
        排序后的新列表
    """
    # 如果列表为空或只有一个元素，直接返回（递归终止条件）
    if len(arr) <= 1:
        return arr
    
    # 1. 选择基准元素（这里选择中间元素）
    pivot = arr[len(arr) // 2]
    
    # 2. 分区操作：将数组分成三个部分
    left = []    # 存放小于基准的元素
    middle = []  # 存放等于基准的元素
    right = []   # 存放大于基准的元素
    
    for num in arr:
        if num < pivot:
            left.append(num)
        elif num == pivot:
            middle.append(num)
        else:
            right.append(num)
    
    # 3. 递归排序左右两部分，然后合并结果
    return quick_sort(left) + middle + quick_sort(right)


def quick_sort_inplace(arr, low=0, high=None):
    """
    原地快速排序（更节省内存的版本）
    
    参数:
        arr: 待排序的列表（直接修改原列表）
        low: 起始索引
        high: 结束索引
    """
    if high is None:
        high = len(arr) - 1
    
    if low < high:
        # 获取分区点索引
        pivot_index = partition(arr, low, high)
        
        # 递归排序基准左右两边的子数组
        quick_sort_inplace(arr, low, pivot_index - 1)
        quick_sort_inplace(arr, pivot_index + 1, high)


def partition(arr, low, high):
    """
    分区函数：选择一个基准，将小于基准的放左边，大于基准的放右边
    
    返回:
        基准元素的最终位置
    """
    # 选择最右边的元素作为基准
    pivot = arr[high]
    
    # i 指向小于基准的区域的边界
    i = low - 1
    
    for j in range(low, high):
        # 如果当前元素小于等于基准，将其交换到左边
        if arr[j] <= pivot:
            i += 1
            arr[i], arr[j] = arr[j], arr[i]
    
    # 将基准放到正确位置（所有小于基准的元素的右边）
    arr[i + 1], arr[high] = arr[high], arr[i + 1]
    
    # 返回基准的索引
    return i + 1


# ========== 测试代码 ==========
if __name__ == "__main__":
    # 测试数据
    test_data = [64, 34, 25, 12, 22, 11, 90, 5, 77, 30]
    
    print("原始数组:", test_data)
    
    # 测试版本1：返回新列表
    sorted_arr = quick_sort(test_data.copy())
    print("快速排序后（版本1 - 返回新列表）:", sorted_arr)
    
    # 测试版本2：原地排序
    test_copy = test_data.copy()
    quick_sort_inplace(test_copy)
    print("快速排序后（版本2 - 原地排序）:", test_copy)
