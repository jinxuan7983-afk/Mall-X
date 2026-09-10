$files = 'GoodsDetail.vue','Cart.vue','Checkout.vue','Cashier.vue','MyOrders.vue','UserLogin.vue'
foreach ($f in $files) {
    $p = "C:\毕业设计\多商家电商平台\mall\mall-vue\src\views\storefront\$f"
    Write-Output "===== $f ====="
    Get-Content $p -Encoding UTF8 | Select-String -Pattern 'placeholder|el-button|@click' | ForEach-Object { $_.Line.Trim() } | Select-Object -First 30
}
