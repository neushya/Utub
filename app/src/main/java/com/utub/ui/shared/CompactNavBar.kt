package com.utub.ui.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** CompactNavBar 항목 정의 */
data class CompactNavItem(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * 컴팩트 하단 탭 바 (개선요구: 기본 NavigationBar 80dp의 2/3 수준, 아이콘+라벨 유지).
 * M3 NavigationBar는 내부 최소 높이 제약으로 축소 시 클리핑되므로 직접 구성한다.
 * 높이 52dp — 각 탭 셀 전체가 터치 타깃이라 접근성 기준(48dp) 충족.
 * 시스템 내비게이션 인셋은 navigationBarsPadding으로 자체 처리 (에지-투-에지 대응).
 */
@Composable
fun CompactNavBar(items: List<CompactNavItem>) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(52.dp),
        ) {
            items.forEach { item ->
                val tint = if (item.selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = item.onClick)
                        // 라벨의 라인높이 여백이 아래로만 쌓여 시각 중심이 위로 쏠리는 것 보정
                        .padding(top = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        item.label,
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        color = tint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
