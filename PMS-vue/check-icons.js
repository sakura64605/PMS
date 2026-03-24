import * as ElementPlusIconsVue from '@element-plus/icons-vue';

const icons = Object.keys(ElementPlusIconsVue);
console.log('All Element Plus Icons:');
for (const icon of icons) {
  console.log(icon);
}

console.log('\nIcons related to thumbs or like:');
const thumbLikeIcons = icons.filter(icon => 
  icon.toLowerCase().includes('thumb') || 
  icon.toLowerCase().includes('like')
);
console.log(thumbLikeIcons);
