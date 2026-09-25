import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeMedia } from '../lib/media.ts';
import { parseYouTubeUrl } from '../lib/youtube.ts';
const id = 'BaW_jenozKc';
const f = (format_id,ext,vcodec,acodec,other={}) => ({format_id,ext,vcodec,acodec,url:'https://example.test/media',...other});
test('canonical URL parsing rejects lookalike hosts, credentials and playlists',() => {
  assert.equal(parseYouTubeUrl(`https://youtu.be/${id}?t=10`),id);
  assert.equal(parseYouTubeUrl(`https://music.youtube.com/watch?v=${id}`),id);
  assert.equal(parseYouTubeUrl('https://www.youtube.com/watch?v=xeMOO5EudYs&list=RDxeMOO5EudYs&start_radio=1'),'xeMOO5EudYs');
  for (const url of [`https://youtube.com.evil.test/watch?v=${id}`,`https://x:y@youtube.com/watch?v=${id}`,'https://youtube.com/playlist?list=abc']) assert.equal(parseYouTubeUrl(url),null);
});
test('4K gets compatible audio; sizes include both streams; codecs preserved',() => {
  const result = normalizeMedia({formats:[f('v','mp4','av01','none',{height:2160,fps:60,filesize:100}),f('a','m4a','none','mp4a',{abr:128,filesize:20})]},id);
  const video = result.options.find(o => o.kind === 'video');
  assert.equal(video.id,'v+a'); assert.equal(video.size,120); assert.equal(video.approximate,false); assert.equal(video.codec,'av01'); assert.equal(video.needsMerge,true);
  assert.equal(result.options.filter(o => o.container === 'MP3').length,4);
});
test('exclude DRM/storyboards, unsupported resolution and video without compatible audio',() => {
  const result = normalizeMedia({formats:[f('drm','mp4','avc1','aac',{height:720,has_drm:true}),f('8k','mp4','av01','aac',{height:4320}),f('silent','webm','vp9','none',{height:2160}),f('image','mhtml','none','none')]},id);
  assert.equal(result.options.length,0);
});
test('unknown companion size must not be advertised as full output size',() => {
  const result = normalizeMedia({formats:[f('v','webm','vp9','none',{height:1440,filesize:100}),f('a','webm','none','opus')]},id);
  assert.equal(result.options[0].size,null);
});
test('source 1080p does not fabricate higher resolution and keeps variants',() => {
  const result = normalizeMedia({formats:[f('one','mp4','avc1','aac',{height:1080}),f('two','mp4','av01','aac',{height:1080})]},id);
  assert.equal(result.options.length,2); assert.ok(result.options.every(o => o.height === 1080));
});
test('duplicate audio streams are collapsed while codec alternatives remain available',() => {
  const result = normalizeMedia({formats:[f('h264','mp4','avc1.640028','aac',{height:1080,fps:30}),f('h264-copy','mp4','avc1.640028','none',{height:1080,fps:30}),f('vp9','mp4','vp09.00.40.08','none',{height:1080,fps:30}),f('audio','m4a','none','mp4a.40.2',{abr:129}),f('audio-copy','m4a','none','mp4a.40.2',{abr:129})]},id);
  assert.equal(result.options.filter(o => o.kind === 'video').length,2);
  assert.equal(result.options.filter(o => o.container === 'M4A').length,1);
});
