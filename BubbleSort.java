/**
 * 冒泡排序（Bubble Sort）Java实现
 * 
 * 原理：重复遍历数组，依次比较相邻两个元素，如果顺序错误就交换。
 * 每轮遍历会将当前未排序部分的最大元素"冒泡"到数组末尾。
 */
public class BubbleSort {

    /**
     * 标准冒泡排序
     * @param arr 待排序数组
     */
    public static void bubbleSort(int[] arr) {
        int n = arr.length;
        // 外层循环：控制排序轮数，最多需要 n-1 轮
        for (int i = 0; i < n - 1; i++) {
            // 内层循环：每轮比较相邻元素
            // 每轮结束后，末尾 i 个元素已有序，无需再比较
            for (int j = 0; j < n - 1 - i; j++) {
                if (arr[j] > arr[j + 1]) {
                    // 交换相邻元素
                    int temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                }
            }
        }
    }

    /**
     * 优化版冒泡排序（增加提前结束标志）
     * 如果某一轮没有发生任何交换，说明数组已有序，提前结束
     * @param arr 待排序数组
     */
    public static void optimizedBubbleSort(int[] arr) {
        int n = arr.length;
        for (int i = 0; i < n - 1; i++) {
            boolean swapped = false; // 标记本轮是否发生交换
            for (int j = 0; j < n - 1 - i; j++) {
                if (arr[j] > arr[j + 1]) {
                    int temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                    swapped = true;
                }
            }
            // 如果没有发生交换，说明数组已有序，直接结束
            if (!swapped) {
                break;
            }
        }
    }

    /**
     * 打印数组
     */
    public static void printArray(int[] arr) {
        for (int i = 0; i < arr.length; i++) {
            System.out.print(arr[i]);
            if (i < arr.length - 1) {
                System.out.print(", ");
            }
        }
        System.out.println();
    }

    /**
     * 测试
     */
    public static void main(String[] args) {
        int[] arr1 = {64, 34, 25, 12, 22, 11, 90};
        System.out.print("原始数组: ");
        printArray(arr1);

        bubbleSort(arr1);
        System.out.print("排序后: ");
        printArray(arr1);

        System.out.println();

        // 测试优化版
        int[] arr2 = {5, 1, 4, 2, 8};
        System.out.print("原始数组: ");
        printArray(arr2);

        optimizedBubbleSort(arr2);
        System.out.print("排序后: ");
        printArray(arr2);
    }
}
