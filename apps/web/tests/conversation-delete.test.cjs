const {test}=require('node:test');
const assert=require('node:assert/strict');
const {loadSource}=require('./source-loader.cjs');
const {ConversationHistoryItem}=loadSource('components/chat/ConversationHistoryItem.tsx');
function nodes(n){if(!n||typeof n!=='object')return [];if(Array.isArray(n))return n.flatMap(nodes);return [n,...nodes(n.props?.children)];}
const conversation={id:'chat',title:'balance.',createdAt:'2026-09-11'};
test('chat history rows expose navigation without a per-chat delete button',()=>{
  let opened=0;
  const row=ConversationHistoryItem({conversation,current:true,disabled:false,onSelect:()=>opened++});
  const buttons=nodes(row).filter(n=>n.type==='button');
  assert.equal(buttons.length,1);
  assert.equal(buttons[0].props.class,'messenger-history-open');
  assert.equal(buttons[0].props['aria-current'],'true');
  buttons[0].props.onClick();
  assert.equal(opened,1);
});
test('chat history preserves bulk selection and disabled controls',()=>{
  let toggled=0;
  const row=ConversationHistoryItem({conversation,current:false,disabled:true,selecting:true,selected:true,onSelect(){},onToggleSelection:()=>toggled++});
  const checkbox=nodes(row).find(n=>n.type==='input');
  assert.equal(checkbox.props.checked,true);
  assert.equal(checkbox.props.disabled,true);
  assert.equal(nodes(row).find(n=>n.type==='button').props.disabled,true);
  checkbox.props.onChange();
  assert.equal(toggled,1);
});
