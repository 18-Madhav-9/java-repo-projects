class TreeNode {
    int val ;
    TreeNode left ;
    TreeNode right ;
    int LeftCount ;

    TreeNode(int data) {
        val = data ;
        left = null ;
        right = null ;
        LeftCount = 1 ;
    }
}
class TreeMethod {
    static void insertNode(TreeNode root ,TreeNode node) {
        if ( root == null ) {
            root = node ;
        }
        else {
            if ( root.val > node.val ) {
                root.LeftCount++ ;
                if (root.left == null ) {
                    root.left = node ;
                }
                else insertNode(root.left, node);
            }
            else {
                if ( root.right == null ) {
                    root.right = node; 
                    root.LeftCount++ ;
                }
                else insertNode(root.right, node) ;
            }
        }
    }

    static void dfs(TreeNode root) {
        if ( root == null ) return ;
        if ( root.left != null ) dfs(root.left) ;
        System.out.println(root.val) ;
        if ( root.right != null ) dfs(root.right) ;
    }

    //K-th smallest number in Augmented BST
    static int kthSmallest(TreeNode root ,int k ) {
        if ( root == null ) return -1 ;
        
        if ( k == root.LeftCount ) return root.val ; 
        else if ( k < root.LeftCount ) {
            return kthSmallest(root.left, k) ;
        }
        else {
            return kthSmallest(root.right, k) ;
        }
    }
}



public class AugBST {
    public static void main(String[] args) {
        TreeNode root = new TreeNode(5) ;
        root.left = new TreeNode(4) ;
        root.right = new TreeNode(6) ;
        TreeMethod.insertNode(root, new TreeNode(3));
        //root.dfs(root) ;
        System.out.println(TreeMethod.kthSmallest(root,1)) ;
        
    }
}